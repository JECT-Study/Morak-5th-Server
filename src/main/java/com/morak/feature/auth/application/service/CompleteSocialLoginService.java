package com.morak.feature.auth.application.service;

import com.morak.feature.auth.application.dto.result.AccessToken;
import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.result.IssuedTokens;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.auth.application.port.out.SocialProfile;
import com.morak.feature.auth.application.port.out.UserAuthPort;
import com.morak.feature.auth.application.port.out.UserAuthPort.UserState;
import com.morak.feature.auth.domain.entity.RefreshToken;
import com.morak.feature.auth.domain.entity.SocialAccount;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import com.morak.feature.auth.domain.exception.DuplicateSocialAccountException;
import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import com.morak.feature.auth.domain.repository.SocialAccountRepository;
import com.morak.feature.auth.domain.vo.AuthUserId;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CompleteSocialLoginService {

	private static final Logger log = LoggerFactory.getLogger(CompleteSocialLoginService.class);

	private final SocialAccountRepository socialAccountRepository;
	private final UserAuthPort userAuthPort;
	private final RefreshTokenRepository refreshTokenRepository;
	private final ServiceTokenIssuer tokenIssuer;
	private final Duration refreshValidity;
	private final TransactionTemplate loginTransaction;
	private final boolean allowRejoin;

	public CompleteSocialLoginService(
			SocialAccountRepository socialAccountRepository,
			UserAuthPort userAuthPort,
			RefreshTokenRepository refreshTokenRepository,
			ServiceTokenIssuer tokenIssuer,
			@Value("${morak.auth.jwt.refresh-token-validity}") Duration refreshValidity,
			PlatformTransactionManager transactionManager,
			@Value("${morak.auth.allow-rejoin:false}") boolean allowRejoin) {
		this.allowRejoin = allowRejoin;
		this.socialAccountRepository = socialAccountRepository;
		this.userAuthPort = userAuthPort;
		this.refreshTokenRepository = refreshTokenRepository;
		this.tokenIssuer = tokenIssuer;
		this.refreshValidity = refreshValidity;
		this.loginTransaction = new TransactionTemplate(transactionManager);
		this.loginTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public IssuedTokens login(SocialProfile profile) {
		try {
			return loginTransaction.execute(status -> completeLogin(profile, null));
		} catch (DuplicateSocialAccountException exception) {
			// execute returns only after the failed transaction has been rolled back.
			return loginTransaction.execute(status -> completeLogin(profile, exception));
		}
	}

	private IssuedTokens completeLogin(SocialProfile profile, DuplicateSocialAccountException conflict) {
		SocialAccount account = socialAccountRepository
				.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
				.orElse(null);
		UserState user;
		if (account == null) {
			if (conflict != null) {
				throw conflict;
			}
			user = createUser(profile);
		} else {
			user = userAuthPort.updateSocialProfile(
					new AuthUserId(account.getUserId()), profile.email())
					.orElseThrow(() -> new BusinessException(AuthErrorCode.KAKAO_UNAVAILABLE));
			if (!user.active() && allowRejoin) {
				// TODO(D-3): 재가입 정책 미확정. 임시로 탈퇴 계정 연결을 끊고 새 사용자로 가입시킨다.
				log.info("탈퇴 계정 재가입 허용: 기존 userId={}", account.getUserId());
				socialAccountRepository.delete(account);
				user = createUser(profile);
			}
		}
		if (!user.active()) {
			throw new BusinessException(AuthErrorCode.WITHDRAWN_USER);
		}

		AccessToken accessToken = tokenIssuer.createAccessToken(user.userId(), user.onboarded());
		String refreshToken = tokenIssuer.createRefreshToken();
		LocalDateTime now = LocalDateTime.now();
		refreshTokenRepository.save(new RefreshToken(
				user.userId().value(), tokenIssuer.hash(refreshToken), now.plus(refreshValidity)));
		return new IssuedTokens(
				accessToken.value(),
				refreshToken,
				"Bearer",
				tokenIssuer.accessValiditySeconds(),
				accessToken.expiresAt(),
				user.onboarded());
	}

	private UserState createUser(SocialProfile profile) {
		UserState user = userAuthPort.createUser(profile.email(), profile.profileImageUrl());
		socialAccountRepository.save(new SocialAccount(user.userId().value(), profile.provider(), profile.providerUserId()));
		return user;
	}
}

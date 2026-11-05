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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CompleteSocialLoginService {

	private final SocialAccountRepository socialAccountRepository;
	private final UserAuthPort userAuthPort;
	private final RefreshTokenRepository refreshTokenRepository;
	private final ServiceTokenIssuer tokenIssuer;
	private final Duration refreshValidity;
	private final TransactionTemplate loginTransaction;

	public CompleteSocialLoginService(
			SocialAccountRepository socialAccountRepository,
			UserAuthPort userAuthPort,
			RefreshTokenRepository refreshTokenRepository,
			ServiceTokenIssuer tokenIssuer,
			@Value("${morak.auth.jwt.refresh-token-validity}") Duration refreshValidity,
			PlatformTransactionManager transactionManager) {
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
		UserState user = socialAccountRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
				.map(account -> userAuthPort.updateSocialProfile(
						new AuthUserId(account.getUserId()), profile.email(), profile.profileImageUrl())
						.orElseThrow(() -> new BusinessException(AuthErrorCode.KAKAO_UNAVAILABLE)))
				.orElseGet(() -> {
					if (conflict != null) {
						throw conflict;
					}
					return createUser(profile);
				});
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

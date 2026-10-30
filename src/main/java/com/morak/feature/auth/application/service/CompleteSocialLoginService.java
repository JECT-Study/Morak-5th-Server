package com.morak.feature.auth.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.IssuedTokens;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.auth.application.port.out.SocialProfile;
import com.morak.feature.auth.domain.entity.RefreshToken;
import com.morak.feature.auth.domain.entity.SocialAccount;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import com.morak.feature.auth.domain.repository.SocialAccountRepository;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteSocialLoginService {

	private final SocialAccountRepository socialAccountRepository;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final ServiceTokenIssuer tokenIssuer;
	private final Duration refreshValidity;

	public CompleteSocialLoginService(
			SocialAccountRepository socialAccountRepository,
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			ServiceTokenIssuer tokenIssuer,
			@Value("${morak.auth.jwt.refresh-token-validity}") Duration refreshValidity) {
		this.socialAccountRepository = socialAccountRepository;
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.tokenIssuer = tokenIssuer;
		this.refreshValidity = refreshValidity;
	}

	@Transactional
	public IssuedTokens login(SocialProfile profile) {
		User user = socialAccountRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
				.map(account -> userRepository.findById(account.getUserId())
						.orElseThrow(() -> new BusinessException(AuthErrorCode.KAKAO_UNAVAILABLE)))
				.orElseGet(() -> createUser(profile));
		user.updateSocialProfile(profile.email(), profile.profileImageUrl());
		if (!user.isActive()) {
			throw new BusinessException(AuthErrorCode.WITHDRAWN_USER);
		}

		String accessToken = tokenIssuer.createAccessToken(user.getId(), user.isOnboarded());
		String refreshToken = tokenIssuer.createRefreshToken();
		LocalDateTime now = LocalDateTime.now();
		refreshTokenRepository.save(new RefreshToken(
				user.getId(), tokenIssuer.hash(refreshToken), now.plus(refreshValidity)));
		return new IssuedTokens(
				accessToken,
				refreshToken,
				"Bearer",
				tokenIssuer.accessValiditySeconds(),
				user.isOnboarded());
	}

	private User createUser(SocialProfile profile) {
		User user = userRepository.save(new User(profile.email(), profile.profileImageUrl()));
		socialAccountRepository.save(new SocialAccount(user.getId(), profile.provider(), profile.providerUserId()));
		return user;
	}
}

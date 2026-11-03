package com.morak.feature.auth.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.command.SocialLoginCommand;
import com.morak.feature.auth.application.dto.result.IssuedTokens;
import com.morak.feature.auth.application.port.out.OAuthProviderException;
import com.morak.feature.auth.application.port.out.SocialOAuthProvider;
import com.morak.feature.auth.application.port.out.SocialProfile;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import org.springframework.stereotype.Service;

@Service
public class KakaoLoginService {

	private final SocialOAuthProviderRegistry providerRegistry;
	private final CompleteSocialLoginService completeSocialLoginService;

	public KakaoLoginService(
			SocialOAuthProviderRegistry providerRegistry,
			CompleteSocialLoginService completeSocialLoginService) {
		this.providerRegistry = providerRegistry;
		this.completeSocialLoginService = completeSocialLoginService;
	}

	public IssuedTokens login(SocialLoginCommand command) {
		SocialOAuthProvider oauthProvider;
		try {
			oauthProvider = providerRegistry.get(command.provider());
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
		}
		SocialProfile profile;
		try {
			profile = oauthProvider.getProfile(command.code());
		} catch (OAuthProviderException exception) {
			throw new BusinessException(exception.isInvalidAuthorization()
					? AuthErrorCode.INVALID_KAKAO_AUTHORIZATION
					: AuthErrorCode.KAKAO_UNAVAILABLE);
		}
		return completeSocialLoginService.login(profile);
	}
}

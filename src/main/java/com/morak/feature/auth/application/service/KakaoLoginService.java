package com.morak.feature.auth.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.IssuedTokens;
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

	public IssuedTokens login(String provider, String authorizationCode) {
		SocialOAuthProvider oauthProvider;
		try {
			oauthProvider = providerRegistry.get(provider);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
		}
		SocialProfile profile;
		try {
			profile = oauthProvider.getProfile(authorizationCode);
		} catch (OAuthProviderException exception) {
			throw new BusinessException(exception.isInvalidAuthorization()
					? AuthErrorCode.INVALID_KAKAO_AUTHORIZATION
					: AuthErrorCode.KAKAO_UNAVAILABLE);
		}
		return completeSocialLoginService.login(profile);
	}
}

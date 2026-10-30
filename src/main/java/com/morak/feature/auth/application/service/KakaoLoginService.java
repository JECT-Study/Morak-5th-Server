package com.morak.feature.auth.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.IssuedTokens;
import com.morak.feature.auth.application.port.out.OAuthProviderException;
import com.morak.feature.auth.application.port.out.SocialProfile;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

	public IssuedTokens login(String authorizationCode) {
		SocialProfile profile;
		try {
			profile = providerRegistry.get("KAKAO").getProfile(authorizationCode);
		} catch (OAuthProviderException exception) {
			throw new BusinessException(exception.isInvalidAuthorization()
					? AuthErrorCode.INVALID_KAKAO_AUTHORIZATION
					: AuthErrorCode.KAKAO_UNAVAILABLE);
		}
		return completeSocialLoginService.login(profile);
	}

	public IssuedTokens loginFromCallback(String authorizationCode, String state, String expectedState) {
		if (!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
				expectedState.getBytes(StandardCharsets.UTF_8))) {
			throw new BusinessException(AuthErrorCode.INVALID_KAKAO_AUTHORIZATION);
		}
		return login(authorizationCode);
	}
}

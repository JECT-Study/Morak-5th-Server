package com.morak.feature.auth.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.ErrorCode;
import com.morak.feature.auth.application.dto.command.SocialLoginCommand;
import com.morak.feature.auth.application.port.out.OAuthProviderException;
import com.morak.feature.auth.application.port.out.SocialOAuthProvider;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import com.morak.feature.auth.domain.vo.SocialProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KakaoLoginServiceTest {

	@Test
	void mapsProviderErrorsToProviderSpecificCodes() {
		assertEquals(AuthErrorCode.INVALID_KAKAO_AUTHORIZATION, loginError(SocialProvider.KAKAO, true));
		assertEquals(AuthErrorCode.KAKAO_UNAVAILABLE, loginError(SocialProvider.KAKAO, false));
		assertEquals(AuthErrorCode.INVALID_GOOGLE_AUTHORIZATION, loginError(SocialProvider.GOOGLE, true));
		assertEquals(AuthErrorCode.GOOGLE_UNAVAILABLE, loginError(SocialProvider.GOOGLE, false));
	}

	@Test
	void rejectsUnknownAndUnregisteredProviders() {
		KakaoLoginService service = new KakaoLoginService(
				new SocialOAuthProviderRegistry(List.of(provider(SocialProvider.KAKAO))), mock(CompleteSocialLoginService.class));
		for (String name : new String[] {"naver", "google"}) {
			assertEquals(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER, assertThrows(BusinessException.class,
					() -> service.login(new SocialLoginCommand(name, "code"))).getErrorCode());
		}
	}

	private ErrorCode loginError(SocialProvider providerName, boolean invalidAuthorization) {
		SocialOAuthProvider provider = provider(providerName);
		when(provider.getProfile("code"))
				.thenThrow(new OAuthProviderException(invalidAuthorization, new RuntimeException()));
		KakaoLoginService service = new KakaoLoginService(
				new SocialOAuthProviderRegistry(List.of(provider)), mock(CompleteSocialLoginService.class));
		return assertThrows(BusinessException.class,
				() -> service.login(new SocialLoginCommand(providerName.name().toLowerCase(), "code"))).getErrorCode();
	}

	private SocialOAuthProvider provider(SocialProvider name) {
		SocialOAuthProvider provider = mock(SocialOAuthProvider.class);
		when(provider.provider()).thenReturn(name);
		return provider;
	}
}

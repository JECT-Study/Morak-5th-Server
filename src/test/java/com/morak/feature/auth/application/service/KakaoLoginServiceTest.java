package com.morak.feature.auth.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.ErrorCode;
import com.morak.feature.auth.application.dto.command.SocialLoginCommand;
import com.morak.feature.auth.application.port.out.OAuthProviderException;
import com.morak.feature.auth.application.port.out.SocialOAuthProvider;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KakaoLoginServiceTest {

	@Test
	void mapsProviderErrorsToProviderSpecificCodes() {
		assertEquals(AuthErrorCode.INVALID_KAKAO_AUTHORIZATION, loginError("KAKAO", true));
		assertEquals(AuthErrorCode.KAKAO_UNAVAILABLE, loginError("KAKAO", false));
		assertEquals(AuthErrorCode.INVALID_GOOGLE_AUTHORIZATION, loginError("GOOGLE", true));
		assertEquals(AuthErrorCode.GOOGLE_UNAVAILABLE, loginError("GOOGLE", false));
	}

	private ErrorCode loginError(String providerName, boolean invalidAuthorization) {
		SocialOAuthProvider provider = mock(SocialOAuthProvider.class);
		when(provider.provider()).thenReturn(providerName);
		when(provider.getProfile("code"))
				.thenThrow(new OAuthProviderException(invalidAuthorization, new RuntimeException()));
		KakaoLoginService service = new KakaoLoginService(
				new SocialOAuthProviderRegistry(List.of(provider)), mock(CompleteSocialLoginService.class));
		return assertThrows(BusinessException.class,
				() -> service.login(new SocialLoginCommand(providerName.toLowerCase(), "code"))).getErrorCode();
	}
}

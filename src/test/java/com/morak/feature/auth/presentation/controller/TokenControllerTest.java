package com.morak.feature.auth.presentation.controller;

import com.morak.common.config.SecurityConfig;
import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.result.IssuedTokens;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.application.service.LogoutService;
import com.morak.feature.auth.application.service.RefreshTokenService;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TokenController.class, properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({SecurityConfig.class, LoginUserJwtConverter.class})
class TokenControllerTest {
	@Autowired private MockMvc mvc;
	@MockitoBean private RefreshTokenService refresh;
	@MockitoBean private LogoutService logout;
	@MockitoBean private AuthenticateUserService users;
	@MockitoBean private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	@Test
	void refreshWithoutAccessTokenReturnsLoginResponse() throws Exception {
		when(refresh.refresh("old")).thenReturn(new IssuedTokens("access", "new", "Bearer", 1800,
				Instant.parse("2026-11-06T12:30:00Z"), true));
		mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"old\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.accessToken").value("access"))
				.andExpect(jsonPath("$.data.refreshToken").value("new"))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
				.andExpect(jsonPath("$.data.accessTokenExpiresAt").value("2026-11-06T12:30:00Z"))
				.andExpect(jsonPath("$.data.onboarded").value(true));
		verifyNoInteractions(users);
	}

	@Test
	void logoutWithoutAccessTokenReturnsEmptySuccess() throws Exception {
		mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"old\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").isEmpty());
		verify(logout).logout("old");
		verifyNoInteractions(users);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "{}", "null", "{\"refreshToken\":null}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":\"  \"}", "{"})
	void rejectsMissingOrBlankTokenOnBothEndpoints(String body) throws Exception {
		for (String endpoint : new String[] {"/auth/refresh", "/auth/logout"}) {
			mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(refresh, logout);
	}

	@ParameterizedTest
	@ValueSource(strings = {"INVALID_REFRESH_TOKEN", "WITHDRAWN_USER"})
	void returnsServiceAuthenticationErrors(String errorName) throws Exception {
		AuthErrorCode error = AuthErrorCode.valueOf(errorName);
		when(refresh.refresh("old")).thenThrow(new BusinessException(error));
		mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"old\"}"))
				.andExpect(status().is(error.status().value()))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(error.code()));
	}

	@Test
	void unrelatedRoutesStillRequireAuthentication() throws Exception {
		mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
		mvc.perform(post("/auth/other")).andExpect(status().isUnauthorized());
	}
}

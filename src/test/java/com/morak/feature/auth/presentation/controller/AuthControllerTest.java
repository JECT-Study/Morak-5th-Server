package com.morak.feature.auth.presentation.controller;

import com.morak.feature.auth.application.dto.command.SocialLoginCommand;
import com.morak.feature.auth.application.dto.result.IssuedTokens;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.application.service.KakaoLoginService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	@MockitoBean
	private KakaoLoginService loginService;

	@MockitoBean
	private AuthenticateUserService authenticateUserService;

	@Test
	void serializesExpirationAsUtcIso8601AndPreservesExistingFields() throws Exception {
		Instant expiresAt = Instant.parse("2026-11-03T12:30:00Z");
		when(loginService.login(new SocialLoginCommand("kakao", "test-code")))
				.thenReturn(new IssuedTokens("test-access", "test-refresh", "Bearer", 1800, expiresAt, false));

		mvc.perform(post("/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"test-code\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.accessTokenExpiresAt").isString())
				.andExpect(jsonPath("$.data.accessTokenExpiresAt").value("2026-11-03T12:30:00Z"))
				.andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
				.andExpect(jsonPath("$.data.accessToken").value("test-access"))
				.andExpect(jsonPath("$.data.refreshToken").value("test-refresh"))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.onboarded").value(false));
	}
}

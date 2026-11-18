package com.morak.feature.notification.presentation.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.morak.common.config.SecurityConfig;
import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import com.morak.feature.notification.application.service.PushTokenService;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
		controllers = PushTokenController.class,
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({SecurityConfig.class, LoginUserJwtConverter.class})
class PushTokenControllerTest {

	private static final long USER_ID = 7L;
	private static final String TOKEN = "ExponentPushToken[abc]";
	private static final String BODY = "{\"token\":\"" + TOKEN + "\"}";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PushTokenService pushTokenService;

	@MockitoBean
	private AuthenticateUserService authenticateUserService;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	private static RequestPostProcessor loggedIn(boolean onboarded) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new LoginUser(new AuthUserId(USER_ID), onboarded), null, List.of()));
	}

	@Test
	void putSavesTokenForLoggedInUserWithUserAgent() throws Exception {
		mvc.perform(put("/push-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.header("User-Agent", "Expo/1.0")
						.content(BODY)
						.with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(pushTokenService).register(USER_ID, TOKEN, "Expo/1.0");
	}

	@Test
	void deleteRemovesTokenOfLoggedInUser() throws Exception {
		mvc.perform(delete("/push-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY)
						.with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(pushTokenService).unregister(USER_ID, TOKEN);
	}

	@Test
	void blankTokenReturnsBadRequestWithoutCallingService() throws Exception {
		mvc.perform(put("/push-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\" \"}")
						.with(loggedIn(true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));

		verifyNoInteractions(pushTokenService);
	}

	@Test
	void missingBodyReturnsBadRequest() throws Exception {
		mvc.perform(put("/push-tokens").contentType(MediaType.APPLICATION_JSON).with(loggedIn(true)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void invalidTokenFormatReturnsServiceError() throws Exception {
		doThrow(new BusinessException(NotificationErrorCode.INVALID_PUSH_TOKEN))
				.when(pushTokenService)
				.register(USER_ID, "bad", null);

		mvc.perform(put("/push-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"bad\"}")
						.with(loggedIn(true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("NOTIFICATION_004"));
	}

	@Test
	void requestsWithoutLoginAreRejected() throws Exception {
		mvc.perform(put("/push-tokens").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized());
		mvc.perform(delete("/push-tokens").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(pushTokenService);
	}

	@Test
	void membersWhoHaveNotFinishedOnboardingAreForbidden() throws Exception {
		mvc.perform(put("/push-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY)
						.with(loggedIn(false)))
				.andExpect(status().isForbidden());

		verifyNoInteractions(pushTokenService);
	}
}

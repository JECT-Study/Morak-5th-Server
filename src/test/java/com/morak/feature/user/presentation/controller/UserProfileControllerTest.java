package com.morak.feature.user.presentation.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.morak.common.config.SecurityConfig;
import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import com.morak.feature.user.application.dto.result.UserProfileView;
import com.morak.feature.user.application.port.out.UserRecordReader.Medal;
import com.morak.feature.user.application.service.ReadUserProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
		controllers = UserProfileController.class,
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({SecurityConfig.class, LoginUserJwtConverter.class})
class UserProfileControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ReadUserProfileService service;

	@MockitoBean
	private AuthenticateUserService authenticateUserService;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	private static RequestPostProcessor loggedIn(boolean onboarded) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new LoginUser(new AuthUserId(1L), onboarded), null, List.of()));
	}

	@Test
	void returnsOtherUsersProfile() throws Exception {
		when(service.read(7L)).thenReturn(new UserProfileView(7L, "러너", null, "매일 달려요", null,
				List.of("EXERCISE"), 80, 3, true, 3, 1, List.of(new Medal("COMPLETE_1", "메달 1-1", null))));

		mvc.perform(get("/users/7").with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("러너"))
				.andExpect(jsonPath("$.data.interests[0]").value("EXERCISE"))
				.andExpect(jsonPath("$.data.certificationRate").value(80))
				.andExpect(jsonPath("$.data.morakiStage").value(3))
				.andExpect(jsonPath("$.data.hasCertificationRecord").value(true))
				.andExpect(jsonPath("$.data.medals[0].code").value("COMPLETE_1"));
	}

	@Test
	void hiddenUserIsNotFound() throws Exception {
		when(service.read(7L)).thenThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

		mvc.perform(get("/users/7").with(loggedIn(true))).andExpect(status().isNotFound());
	}

	@Test
	void nonNumericUserIdIsBadRequest() throws Exception {
		mvc.perform(get("/users/abc").with(loggedIn(true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_001"));

		verifyNoInteractions(service);
	}

	@Test
	void callerMustBeLoggedInAndOnboarded() throws Exception {
		mvc.perform(get("/users/7")).andExpect(status().isUnauthorized());
		mvc.perform(get("/users/7").with(loggedIn(false))).andExpect(status().isForbidden());

		verifyNoInteractions(service);
	}
}

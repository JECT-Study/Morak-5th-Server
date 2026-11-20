package com.morak.feature.participation.presentation.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.morak.common.config.SecurityConfig;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import com.morak.feature.participation.application.dto.result.MyRecordView;
import com.morak.feature.participation.application.service.ReadMyRecordService;
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
		controllers = MyRecordController.class,
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({SecurityConfig.class, LoginUserJwtConverter.class})
class MyRecordControllerTest {

	private static final long USER_ID = 1L;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ReadMyRecordService service;

	@MockitoBean
	private AuthenticateUserService authenticateUserService;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	private static RequestPostProcessor loggedIn() {
		return authentication(new UsernamePasswordAuthenticationToken(
				new LoginUser(new AuthUserId(USER_ID), true), null, List.of()));
	}

	@Test
	void returnsMyRecord() throws Exception {
		when(service.read(USER_ID)).thenReturn(new MyRecordView(80, 3, true, 3, 1));

		mvc.perform(get("/users/me/records").with(loggedIn()))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.certificationRate").value(80))
				.andExpect(jsonPath("$.data.morakiStage").value(3))
				.andExpect(jsonPath("$.data.hasCertificationRecord").value(true))
				.andExpect(jsonPath("$.data.completedCount").value(3))
				.andExpect(jsonPath("$.data.midwayFailCount").value(1));
	}

	@Test
	void noCertificationRecordHasNullRateAndStage() throws Exception {
		when(service.read(USER_ID)).thenReturn(new MyRecordView(null, null, false, 0, 0));

		mvc.perform(get("/users/me/records").with(loggedIn()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.certificationRate").isEmpty())
				.andExpect(jsonPath("$.data.morakiStage").isEmpty())
				.andExpect(jsonPath("$.data.hasCertificationRecord").value(false));
	}

	@Test
	void requestsWithoutLoginAreRejected() throws Exception {
		mvc.perform(get("/users/me/records")).andExpect(status().isUnauthorized());

		verifyNoInteractions(service);
	}
}

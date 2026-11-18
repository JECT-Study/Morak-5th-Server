package com.morak.feature.participation.presentation.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.morak.common.config.SecurityConfig;
import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import com.morak.feature.participation.application.dto.query.ApplicationListStatus;
import com.morak.feature.participation.application.dto.result.ApplicationView;
import com.morak.feature.participation.application.dto.result.ApplicationView.Applicant;
import com.morak.feature.participation.application.service.ReadApplicationsService;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.time.Instant;
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
		controllers = ParticipationRequestController.class,
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({SecurityConfig.class, LoginUserJwtConverter.class})
class ParticipationRequestControllerTest {

	private static final long USER_ID = 1L;
	private static final long CHALLENGE = 10L;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ReadApplicationsService service;

	@MockitoBean
	private AuthenticateUserService authenticateUserService;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	private static RequestPostProcessor loggedIn(boolean onboarded) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new LoginUser(new AuthUserId(USER_ID), onboarded), null, List.of()));
	}

	@Test
	void listReturnsApplicationsWithApplicantProfile() throws Exception {
		when(service.list(USER_ID, CHALLENGE, ApplicationListStatus.PENDING))
				.thenReturn(List.of(new ApplicationView(
						44L,
						"PENDING",
						"열심히 하겠습니다",
						Instant.parse("2026-11-17T01:00:00Z"),
						new Applicant(
								7L,
								"러너",
								"https://img/7.png",
								80,
								3,
								3,
								1,
								List.of(new ApplicationView.Medal("COMPLETE_1", "완주 메달", "https://m/1.png"))))));

		mvc.perform(get("/challenges/10/participation-requests").with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[0].participationId").value(44))
				.andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
				.andExpect(jsonPath("$.data.items[0].message").value("열심히 하겠습니다"))
				.andExpect(jsonPath("$.data.items[0].appliedAt").value("2026-11-17T01:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].applicant.userId").value(7))
				.andExpect(jsonPath("$.data.items[0].applicant.nickname").value("러너"))
				.andExpect(jsonPath("$.data.items[0].applicant.profileImageUrl").value("https://img/7.png"))
				.andExpect(jsonPath("$.data.items[0].applicant.certificationRate").value(80))
				.andExpect(jsonPath("$.data.items[0].applicant.morakiStage").value(3))
				.andExpect(jsonPath("$.data.items[0].applicant.completedCount").value(3))
				.andExpect(jsonPath("$.data.items[0].applicant.midwayFailCount").value(1))
				.andExpect(jsonPath("$.data.items[0].applicant.medals[0].code").value("COMPLETE_1"))
				.andExpect(jsonPath("$.data.items[0].applicant.medals[0].name").value("완주 메달"))
				.andExpect(jsonPath("$.data.items[0].applicant.medals[0].iconUrl").value("https://m/1.png"));
	}

	@Test
	void applicantWithoutCertificationRecordHasNullRateAndStage() throws Exception {
		when(service.list(USER_ID, CHALLENGE, ApplicationListStatus.PENDING))
				.thenReturn(List.of(new ApplicationView(
						44L, "PENDING", null, Instant.parse("2026-11-17T01:00:00Z"),
						new Applicant(7L, "새내기", null, null, null, 0, 0, List.of()))));

		mvc.perform(get("/challenges/10/participation-requests").with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].applicant.certificationRate").isEmpty())
				.andExpect(jsonPath("$.data.items[0].applicant.morakiStage").isEmpty())
				.andExpect(jsonPath("$.data.items[0].applicant.medals").isEmpty());
	}

	@Test
	void statusQueryIsPassedToService() throws Exception {
		when(service.list(USER_ID, CHALLENGE, ApplicationListStatus.REJECTED)).thenReturn(List.of());

		mvc.perform(get("/challenges/10/participation-requests").param("status", "REJECTED").with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void unknownStatusReturnsBadRequest() throws Exception {
		mvc.perform(get("/challenges/10/participation-requests").param("status", "UNKNOWN").with(loggedIn(true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_001"));

		verifyNoInteractions(service);
	}

	@Test
	void nonHostReturnsForbidden() throws Exception {
		when(service.list(USER_ID, CHALLENGE, ApplicationListStatus.PENDING))
				.thenThrow(new BusinessException(ParticipationErrorCode.NOT_CHALLENGE_HOST));

		mvc.perform(get("/challenges/10/participation-requests").with(loggedIn(true)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("PARTICIPATION_NOT_CHALLENGE_HOST"));
	}

	@Test
	void requestsWithoutLoginAreRejected() throws Exception {
		mvc.perform(get("/challenges/10/participation-requests")).andExpect(status().isUnauthorized());

		verifyNoInteractions(service);
	}
}

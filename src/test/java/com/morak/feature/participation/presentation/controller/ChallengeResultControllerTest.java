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
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Member;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Mine;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Team;
import com.morak.feature.participation.application.service.ReadChallengeResultsService;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.math.BigDecimal;
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
		controllers = ChallengeResultController.class,
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({SecurityConfig.class, LoginUserJwtConverter.class})
class ChallengeResultControllerTest {

	private static final long USER_ID = 1L;
	private static final long CHALLENGE = 10L;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ReadChallengeResultsService service;

	@MockitoBean
	private AuthenticateUserService authenticateUserService;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	private static RequestPostProcessor loggedIn() {
		return authentication(new UsernamePasswordAuthenticationToken(
				new LoginUser(new AuthUserId(USER_ID), true), null, List.of()));
	}

	@Test
	void teamResultReturnsRateAndMembers() throws Exception {
		when(service.team(USER_ID, CHALLENGE)).thenReturn(new Team(
				21, 24, new BigDecimal("87.50"),
				List.of(new Member(101L, 2L, "퇴장러", null, 7, 3, new BigDecimal("70.00"), false, true))));

		mvc.perform(get("/challenges/10/results").with(loggedIn()))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.teamApprovedCount").value(21))
				.andExpect(jsonPath("$.data.teamTotalCount").value(24))
				.andExpect(jsonPath("$.data.teamAchievementRate").value(87.5))
				.andExpect(jsonPath("$.data.members[0].participationId").value(101))
				.andExpect(jsonPath("$.data.members[0].nickname").value("퇴장러"))
				.andExpect(jsonPath("$.data.members[0].approvedCount").value(7))
				.andExpect(jsonPath("$.data.members[0].failedCount").value(3))
				.andExpect(jsonPath("$.data.members[0].expelled").value(true))
				.andExpect(jsonPath("$.data.members[0].completed").value(false));
	}

	@Test
	void myResultReturnsRatesAndCompletion() throws Exception {
		when(service.mine(USER_ID, CHALLENGE)).thenReturn(new Mine(
				101L, 7, 10, new BigDecimal("70.00"), new BigDecimal("87.50"), true, false, 2, 5,
				Instant.parse("2026-11-20T03:05:00Z")));

		mvc.perform(get("/challenges/10/results/me").with(loggedIn()))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.participationId").value(101))
				.andExpect(jsonPath("$.data.achievementRate").value(70.0))
				.andExpect(jsonPath("$.data.teamAchievementRate").value(87.5))
				.andExpect(jsonPath("$.data.completed").value(true))
				.andExpect(jsonPath("$.data.expelled").value(false))
				.andExpect(jsonPath("$.data.maxStreak").value(5))
				.andExpect(jsonPath("$.data.finalizedAt").value("2026-11-20T03:05:00Z"));
	}

	@Test
	void nonParticipantReturnsForbidden() throws Exception {
		when(service.team(USER_ID, CHALLENGE))
				.thenThrow(new BusinessException(ParticipationErrorCode.NOT_CHALLENGE_PARTICIPANT));

		mvc.perform(get("/challenges/10/results").with(loggedIn()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("PARTICIPATION_NOT_CHALLENGE_PARTICIPANT"));
	}

	@Test
	void resultNotFinalizedReturnsConflict() throws Exception {
		when(service.mine(USER_ID, CHALLENGE))
				.thenThrow(new BusinessException(ParticipationErrorCode.RESULT_NOT_FINALIZED));

		mvc.perform(get("/challenges/10/results/me").with(loggedIn()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PARTICIPATION_RESULT_NOT_FINALIZED"));
	}

	@Test
	void requestsWithoutLoginAreRejected() throws Exception {
		mvc.perform(get("/challenges/10/results")).andExpect(status().isUnauthorized());
		mvc.perform(get("/challenges/10/results/me")).andExpect(status().isUnauthorized());

		verifyNoInteractions(service);
	}
}

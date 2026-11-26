package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.common.exception.ErrorCode;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Member;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Mine;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Team;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader.ApplicantSource;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationChallenges.ChallengeInfo;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.MyParticipation;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipantInfo;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipantResult;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReadChallengeResultsServiceTest {

	private static final long ME = 1L;
	private static final long CHALLENGE = 10L;
	private static final Instant FINALIZED_AT = Instant.parse("2026-11-20T03:05:00Z");

	private final ApplicationChallenges challenges = mock(ApplicationChallenges.class);
	private final ParticipationQuery participationQuery = mock(ParticipationQuery.class);
	private final ApplicantProfileReader profiles = mock(ApplicantProfileReader.class);
	private final ReadChallengeResultsService service =
			new ReadChallengeResultsService(challenges, participationQuery, profiles);

	// ---- 팀 결과 ----

	@Test
	void teamResultSumsMembersAndMarksExpelled() {
		givenParticipant("COMPLETED");
		when(participationQuery.findResults(CHALLENGE)).thenReturn(List.of(
				result(100L, 1L, 14, 14, "100.00", true),
				result(101L, 2L, 7, 10, "70.00", false)));
		when(participationQuery.findParticipantsByIds(any())).thenReturn(Map.of(
				100L, new ParticipantInfo(100L, 1L, "HOST", "COMPLETED"),
				101L, new ParticipantInfo(101L, 2L, "MEMBER", "EXPELLED")));
		when(profiles.findByUserIds(any())).thenReturn(Map.of(
				1L, new ApplicantSource("방장", "https://img/1.png", 0, 0, List.of()),
				2L, new ApplicantSource("퇴장러", null, 0, 0, List.of())));

		Team team = service.team(ME, CHALLENGE);

		// 팀 달성률 = 전체 성공 ÷ 전체 대상 (참가자별 합산): 21 ÷ 24 = 87.5%
		assertEquals(21, team.teamApprovedCount());
		assertEquals(24, team.teamTotalCount());
		assertEquals(new BigDecimal("87.50"), team.teamAchievementRate());
		assertEquals(2, team.members().size());
		Member host = team.members().get(0);
		assertEquals("방장", host.nickname());
		assertEquals(0, host.failedCount());
		assertFalse(host.expelled());
		assertTrue(host.completed());
		Member expelled = team.members().get(1);
		assertEquals("퇴장러", expelled.nickname());
		assertNull(expelled.profileImageUrl());
		assertEquals(3, expelled.failedCount());
		assertTrue(expelled.expelled());
		assertFalse(expelled.completed());
	}

	@Test
	void teamResultKeepsMemberWhoseProfileIsGone() {
		givenParticipant("COMPLETED");
		when(participationQuery.findResults(CHALLENGE)).thenReturn(List.of(result(100L, 1L, 5, 5, "100.00", true)));
		when(participationQuery.findParticipantsByIds(any())).thenReturn(Map.of());
		when(profiles.findByUserIds(any())).thenReturn(Map.of());

		Member member = service.team(ME, CHALLENGE).members().get(0);

		assertNull(member.nickname());
		assertFalse(member.expelled());
	}

	@Test
	void expelledParticipantCanAlsoViewTeamResult() {
		givenParticipant("EXPELLED");
		when(participationQuery.findResults(CHALLENGE)).thenReturn(List.of(result(100L, 1L, 2, 4, "50.00", false)));
		when(participationQuery.findParticipantsByIds(any())).thenReturn(Map.of());
		when(profiles.findByUserIds(any())).thenReturn(Map.of());

		assertEquals(1, service.team(ME, CHALLENGE).members().size());
	}

	// ---- 내 결과 ----

	@Test
	void myResultHasMyRateAndTeamRate() {
		givenParticipant("COMPLETED", 101L);
		when(participationQuery.findResults(CHALLENGE)).thenReturn(List.of(
				result(100L, 1L, 14, 14, "100.00", true),
				result(101L, ME, 7, 10, "70.00", false, 2, 5)));

		Mine mine = service.mine(ME, CHALLENGE);

		assertEquals(101L, mine.participationId());
		assertEquals(7, mine.approvedCount());
		assertEquals(10, mine.totalCount());
		assertEquals(new BigDecimal("70.00"), mine.achievementRate());
		assertEquals(new BigDecimal("87.50"), mine.teamAchievementRate());
		assertFalse(mine.completed());
		assertFalse(mine.expelled());
		assertEquals(2, mine.currentStreak());
		assertEquals(5, mine.maxStreak());
		assertEquals(FINALIZED_AT, mine.finalizedAt());
	}

	@Test
	void myResultShowsExpelledForExpelledParticipant() {
		givenParticipant("EXPELLED", 101L);
		when(participationQuery.findResults(CHALLENGE)).thenReturn(List.of(result(101L, ME, 1, 4, "25.00", false)));

		assertTrue(service.mine(ME, CHALLENGE).expelled());
	}

	@Test
	void myResultIsNotFoundWhenMyRowIsMissing() {
		givenParticipant("ACTIVE", 101L);
		when(participationQuery.findResults(CHALLENGE)).thenReturn(List.of(result(100L, 1L, 14, 14, "100.00", true)));

		assertCode(CommonErrorCode.RESOURCE_NOT_FOUND, () -> service.mine(ME, CHALLENGE));
	}

	// ---- 공통 거절 ----

	@Test
	void nonParticipantIsForbidden() {
		givenChallengeExists();
		when(participationQuery.findMyParticipation(CHALLENGE, ME)).thenReturn(Optional.empty());

		assertCode(ParticipationErrorCode.NOT_CHALLENGE_PARTICIPANT, () -> service.team(ME, CHALLENGE));
		assertCode(ParticipationErrorCode.NOT_CHALLENGE_PARTICIPANT, () -> service.mine(ME, CHALLENGE));
		verify(participationQuery, org.mockito.Mockito.never()).findResults(CHALLENGE);
	}

	@Test
	void applicantsWhoNeverJoinedAreForbidden() {
		for (String status : List.of("PENDING", "REJECTED", "INVALIDATED", "DISCARDED")) {
			givenParticipant(status);

			assertCode(ParticipationErrorCode.NOT_CHALLENGE_PARTICIPANT, () -> service.team(ME, CHALLENGE));
		}
	}

	@Test
	void resultNotFinalizedYetIsConflict() {
		givenParticipant("ACTIVE");
		when(participationQuery.findResults(CHALLENGE)).thenReturn(List.of());

		assertCode(ParticipationErrorCode.RESULT_NOT_FINALIZED, () -> service.team(ME, CHALLENGE));
		assertCode(ParticipationErrorCode.RESULT_NOT_FINALIZED, () -> service.mine(ME, CHALLENGE));
	}

	@Test
	void unknownChallengeIsNotFound() {
		when(challenges.find(CHALLENGE)).thenReturn(Optional.empty());

		assertCode(CommonErrorCode.RESOURCE_NOT_FOUND, () -> service.team(ME, CHALLENGE));
		verifyNoInteractions(participationQuery);
	}

	// ---- 준비 ----

	private void givenChallengeExists() {
		when(challenges.find(CHALLENGE))
				.thenReturn(Optional.of(new ChallengeInfo(CHALLENGE, 1L, "챌린지", false, null, false, false, 0, 0)));
	}

	private void givenParticipant(String status) {
		givenParticipant(status, 100L);
	}

	private void givenParticipant(String status, long participationId) {
		givenChallengeExists();
		when(participationQuery.findMyParticipation(CHALLENGE, ME)).thenReturn(Optional.of(
				new MyParticipation(participationId, CHALLENGE, "MEMBER", status, null, null, null, null, null)));
	}

	private static ParticipantResult result(
			long participationId, long userId, int approved, int total, String rate, boolean completed) {
		return result(participationId, userId, approved, total, rate, completed, 0, 0);
	}

	private static ParticipantResult result(long participationId, long userId, int approved, int total, String rate,
			boolean completed, int currentStreak, int maxStreak) {
		return new ParticipantResult(participationId, userId, approved, total, new BigDecimal(rate), currentStreak,
				maxStreak, completed, FINALIZED_AT);
	}

	private static void assertCode(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
		BusinessException exception = assertThrows(BusinessException.class, action);
		assertEquals(expected.code(), exception.getErrorCode().code());
	}
}

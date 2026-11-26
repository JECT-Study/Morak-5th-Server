package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.common.exception.ErrorCode;
import com.morak.feature.participation.application.dto.query.ApplicationListStatus;
import com.morak.feature.participation.application.dto.result.ApplicationView;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader.ApplicantSource;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader.Medal;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationChallenges.ChallengeInfo;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.Application;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipationRecord;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadApplicationsServiceTest {

	private static final long HOST = 1L;
	private static final long CHALLENGE = 10L;
	private static final Instant APPLIED_AT = Instant.parse("2026-11-17T01:00:00Z");

	private final ApplicationChallenges challenges = mock(ApplicationChallenges.class);
	private final ParticipationQuery participationQuery = mock(ParticipationQuery.class);
	private final ApplicantProfileReader profiles = mock(ApplicantProfileReader.class);
	private final ReadApplicationsService service =
			new ReadApplicationsService(challenges, participationQuery, profiles);

	@Test
	void returnsPendingApplicationsWithApplicantProfile() {
		givenChallengeOwnedBy(HOST);
		when(participationQuery.findPendingApplications(CHALLENGE))
				.thenReturn(List.of(new Application(44L, 7L, "열심히 하겠습니다", APPLIED_AT, false)));
		when(profiles.findByUserIds(Set.of(7L)))
				.thenReturn(Map.of(7L, new ApplicantSource(
						"러너", "https://img/7.png", 20, 25, List.of(new Medal("COMPLETE_1", "완주 메달", "https://m/1.png")))));
		when(participationQuery.findRecordsByUserIds(Set.of(7L))).thenReturn(Map.of(7L, new ParticipationRecord(3, 1)));

		List<ApplicationView> result = service.list(HOST, CHALLENGE, ApplicationListStatus.PENDING);

		assertEquals(1, result.size());
		ApplicationView view = result.get(0);
		assertEquals(44L, view.participationId());
		assertEquals("PENDING", view.status());
		assertEquals("열심히 하겠습니다", view.message());
		assertEquals(APPLIED_AT, view.appliedAt());
		ApplicationView.Applicant applicant = view.applicant();
		assertEquals(7L, applicant.userId());
		assertEquals("러너", applicant.nickname());
		assertEquals("https://img/7.png", applicant.profileImageUrl());
		assertEquals(80, applicant.certificationRate());
		assertEquals(3, applicant.morakiStage());
		assertEquals(3, applicant.completedCount());
		assertEquals(1, applicant.midwayFailCount());
		assertEquals(List.of(new ApplicationView.Medal("COMPLETE_1", "완주 메달", "https://m/1.png")), applicant.medals());
	}

	@Test
	void applicantWithoutCertificationRecordHasNullRateAndStage() {
		givenChallengeOwnedBy(HOST);
		when(participationQuery.findPendingApplications(CHALLENGE))
				.thenReturn(List.of(new Application(44L, 7L, null, APPLIED_AT, false)));
		when(profiles.findByUserIds(Set.of(7L)))
				.thenReturn(Map.of(7L, new ApplicantSource("새내기", null, 0, 0, List.of())));
		when(participationQuery.findRecordsByUserIds(Set.of(7L))).thenReturn(Map.of());

		ApplicationView view = service.list(HOST, CHALLENGE, ApplicationListStatus.PENDING).get(0);

		assertNull(view.message());
		assertNull(view.applicant().certificationRate());
		assertNull(view.applicant().morakiStage());
		assertEquals(0, view.applicant().completedCount());
		assertEquals(0, view.applicant().midwayFailCount());
		assertTrue(view.applicant().medals().isEmpty());
	}

	@Test
	void invalidatedApplicationIsReportedAsInvalidated() {
		givenChallengeOwnedBy(HOST);
		when(participationQuery.findPendingApplications(CHALLENGE))
				.thenReturn(List.of(new Application(44L, 7L, "각오", APPLIED_AT, true)));
		when(profiles.findByUserIds(Set.of(7L)))
				.thenReturn(Map.of(7L, new ApplicantSource("러너", null, 10, 10, List.of())));
		when(participationQuery.findRecordsByUserIds(Set.of(7L))).thenReturn(Map.of());

		ApplicationView view = service.list(HOST, CHALLENGE, ApplicationListStatus.PENDING).get(0);

		assertEquals("INVALIDATED", view.status());
		assertEquals(5, view.applicant().morakiStage());
	}

	@Test
	void decidedStatusesUseTheDecidedApplicationQuery() {
		givenChallengeOwnedBy(HOST);
		when(participationQuery.findDecidedApplications(CHALLENGE, "REJECTED"))
				.thenReturn(List.of(new Application(45L, 8L, "각오", APPLIED_AT, false)));
		when(profiles.findByUserIds(Set.of(8L))).thenReturn(Map.of());
		when(participationQuery.findRecordsByUserIds(Set.of(8L))).thenReturn(Map.of());

		ApplicationView view = service.list(HOST, CHALLENGE, ApplicationListStatus.REJECTED).get(0);

		assertEquals("REJECTED", view.status());
		assertNull(view.applicant().nickname());
		verify(participationQuery).findDecidedApplications(CHALLENGE, "REJECTED");
	}

	@Test
	void emptyListDoesNotLoadProfiles() {
		givenChallengeOwnedBy(HOST);
		when(participationQuery.findPendingApplications(CHALLENGE)).thenReturn(List.of());

		assertTrue(service.list(HOST, CHALLENGE, ApplicationListStatus.PENDING).isEmpty());
		verifyNoInteractions(profiles);
	}

	@Test
	void rejectsNonHost() {
		givenChallengeOwnedBy(HOST);

		assertError(ParticipationErrorCode.NOT_CHALLENGE_HOST, () -> service.list(99L, CHALLENGE, ApplicationListStatus.PENDING));
		verifyNoInteractions(profiles);
	}

	@Test
	void unknownChallengeIsNotFound() {
		when(challenges.find(CHALLENGE)).thenReturn(Optional.empty());

		assertError(CommonErrorCode.RESOURCE_NOT_FOUND, () -> service.list(HOST, CHALLENGE, ApplicationListStatus.PENDING));
	}

	private void givenChallengeOwnedBy(long ownerUserId) {
		when(challenges.find(CHALLENGE))
				.thenReturn(Optional.of(new ChallengeInfo(CHALLENGE, ownerUserId, "아침 독서", false, "123456", true, false, 0, 3)));
	}

	private static void assertError(ErrorCode expected, Runnable action) {
		BusinessException exception = assertThrows(BusinessException.class, action::run);
		assertEquals(expected, exception.getErrorCode());
	}
}

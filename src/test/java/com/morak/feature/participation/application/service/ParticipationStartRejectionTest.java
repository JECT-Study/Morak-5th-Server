package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.application.command.ParticipationCommand.RejectedApplication;
import com.morak.feature.participation.application.port.out.ApplicantPoints;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationNotifier;
import com.morak.feature.participation.domain.entity.Participation;

import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import com.morak.feature.participation.domain.repository.ParticipationResultRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 시작 시 남은 승인 대기 신청의 자동 거절(위키 O-21, PM 확정 2026-11-21). */
class ParticipationStartRejectionTest {

	private static final long CHALLENGE = 10L;
	private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");

	private final ParticipationRepository repository = mock(ParticipationRepository.class);
	private final ApplicantPoints points = mock(ApplicantPoints.class);
	private final ApplicationNotifier notifier = mock(ApplicationNotifier.class);
	private final ParticipationCommandService service = new ParticipationCommandService(
			repository, mock(ParticipationResultRepository.class), mock(ApplicationChallenges.class), points, notifier);

	@Test
	void locksOnlyPointHoldingApplicantsInUserIdOrder() {
		when(repository.findPendingByChallenge(CHALLENGE)).thenReturn(List.of(
				pending(103L, 9L, 1_000), pending(101L, 4L, 0), pending(102L, 3L, 1_000)));

		Set<Long> locked = service.lockPendingPointAccounts(CHALLENGE);

		assertEquals(List.of(3L, 9L), List.copyOf(locked));
		verify(points).lockAccounts(locked);
	}

	@Test
	void rejectsEveryPendingApplicationRefundsPointsAndNotifies() {
		Participation withPoint = pending(101L, 3L, 1_000);
		Participation withoutPoint = pending(102L, 4L, 0);
		when(repository.findPendingByChallengeForUpdate(CHALLENGE)).thenReturn(List.of(withPoint, withoutPoint));

		List<RejectedApplication> rejected = service.rejectPendingOnStart(CHALLENGE, "아침 독서", Set.of(3L));

		assertEquals(List.of(new RejectedApplication(101L, 3L, 1_000), new RejectedApplication(102L, 4L, 0)), rejected);
		assertEquals(ParticipationStatus.REJECTED, withPoint.getStatus());
		assertFalse(withPoint.isPointHeld());
		assertEquals(ParticipationStatus.REJECTED, withoutPoint.getStatus());
		verify(points).release(3L, 101L, "아침 독서", 1_000);
		verify(points, never()).release(4L, 102L, "아침 독서", 0);
		verify(notifier).rejected(3L, CHALLENGE, 101L, "아침 독서", 1_000);
		verify(notifier).rejected(4L, CHALLENGE, 102L, "아침 독서", 0);
	}

	@Test
	void newPointApplicantWhoseAccountWasNotLockedRollsBackEverything() {
		Participation locked = pending(101L, 3L, 1_000);
		Participation late = pending(102L, 8L, 1_000);
		when(repository.findPendingByChallengeForUpdate(CHALLENGE)).thenReturn(List.of(locked, late));

		BusinessException error = assertThrows(BusinessException.class,
				() -> service.rejectPendingOnStart(CHALLENGE, "아침 독서", Set.of(3L)));

		assertEquals(ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED, error.getErrorCode());
		assertEquals(ParticipationStatus.PENDING, locked.getStatus());
		verify(points, never()).release(anyLong(), anyLong(), anyString(), anyInt());
		verify(notifier, never()).rejected(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	void noPendingApplicationsDoesNothing() {
		when(repository.findPendingByChallengeForUpdate(CHALLENGE)).thenReturn(List.of());

		assertEquals(List.of(), service.rejectPendingOnStart(CHALLENGE, "아침 독서", Set.of()));
	}

	private static Participation pending(long id, long userId, int pointAmount) {
		Participation application = Participation.apply(CHALLENGE, userId, "각오", pointAmount, NOW);
		ReflectionTestUtils.setField(application, "id", id);
		return application;
	}
}

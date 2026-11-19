package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.dto.command.RescheduleChallengeCommand;
import com.morak.feature.challenge.application.dto.result.RescheduledChallenge;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader.WaitingParticipant;
import com.morak.feature.challenge.application.port.out.ChallengeScheduleResetNotice;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RescheduleChallengeServiceTest {

	private static final long HOST = 1L;
	private static final long CHALLENGE = 10L;
	/** KST 2026-11-18 09:00 */
	private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
	private static final LocalDate NEW_START = LocalDate.of(2026, 11, 25);
	private static final LocalDate NEW_END = LocalDate.of(2026, 12, 1);

	private final ChallengeRepository challenges = mock(ChallengeRepository.class);
	private final ChallengeParticipationReader participants = mock(ChallengeParticipationReader.class);
	private final ChallengeScheduleResetNotice notice = mock(ChallengeScheduleResetNotice.class);
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final RescheduleChallengeService service =
			new RescheduleChallengeService(challenges, participants, Optional.of(notice), clock);

	@Test
	void reschedulesAndNotifiesEveryWaitingParticipant() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD);
		returns(challenge);
		when(participants.findWaitingParticipants(CHALLENGE))
				.thenReturn(List.of(new WaitingParticipant(101, 2L), new WaitingParticipant(102, 3L)));

		RescheduledChallenge result = service.reschedule(HOST, CHALLENGE, command(NEW_START, NEW_END));

		assertEquals(new RescheduledChallenge(CHALLENGE, "RECRUITING", NEW_START, NEW_END), result);
		assertEquals(ChallengeStatus.RECRUITING, challenge.getStatus());
		verify(notice).scheduleReset(2L, CHALLENGE, 101, "아침 독서", NEW_START, NEW_END);
		verify(notice).scheduleReset(3L, CHALLENGE, 102, "아침 독서", NEW_START, NEW_END);
	}

	@Test
	void withoutRecipientsSucceedsEvenIfNoticePortIsMissing() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD);
		returns(challenge);
		when(participants.findWaitingParticipants(CHALLENGE)).thenReturn(List.of());
		RescheduleChallengeService withoutNotice =
				new RescheduleChallengeService(challenges, participants, Optional.empty(), clock);

		RescheduledChallenge result = withoutNotice.reschedule(HOST, CHALLENGE, command(NEW_START, NEW_END));

		assertEquals("RECRUITING", result.status());
	}

	@Test
	void missingNoticePortWithRecipientsFailsWith503() {
		returns(challenge(ChallengeStatus.START_HELD));
		when(participants.findWaitingParticipants(CHALLENGE)).thenReturn(List.of(new WaitingParticipant(101, 2L)));
		RescheduleChallengeService withoutNotice =
				new RescheduleChallengeService(challenges, participants, Optional.empty(), clock);

		BusinessException error = assertThrows(BusinessException.class,
				() -> withoutNotice.reschedule(HOST, CHALLENGE, command(NEW_START, NEW_END)));

		assertEquals(ChallengeErrorCode.RESCHEDULE_INTEGRATION_UNAVAILABLE, error.getErrorCode());
	}

	@Test
	void onlyOwnerCanReschedule() {
		returns(challenge(ChallengeStatus.START_HELD));

		BusinessException error = assertThrows(BusinessException.class,
				() -> service.reschedule(2L, CHALLENGE, command(NEW_START, NEW_END)));

		assertEquals(ChallengeErrorCode.NOT_OWNER, error.getErrorCode());
		verifyNoInteractions(participants, notice);
	}

	@Test
	void missingChallengeIsNotFound() {
		when(challenges.findByIdForUpdate(CHALLENGE)).thenReturn(Optional.empty());

		BusinessException error = assertThrows(BusinessException.class,
				() -> service.reschedule(HOST, CHALLENGE, command(NEW_START, NEW_END)));

		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
	}

	@Test
	void challengeThatIsNotStartHeldCannotBeRescheduled() {
		for (ChallengeStatus status : List.of(ChallengeStatus.RECRUITING, ChallengeStatus.IN_PROGRESS,
				ChallengeStatus.FINALIZING, ChallengeStatus.ENDED, ChallengeStatus.DISCARDED)) {
			Challenge challenge = challenge(status);
			returns(challenge);

			BusinessException error = assertThrows(BusinessException.class,
					() -> service.reschedule(HOST, CHALLENGE, command(NEW_START, NEW_END)));

			assertEquals(ChallengeErrorCode.NOT_START_HELD, error.getErrorCode());
			assertEquals(status, challenge.getStatus());
		}
		verifyNoInteractions(participants, notice);
	}

	@Test
	void sameRequestAgainReturnsSameResponseWithoutNotifying() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD);
		returns(challenge);
		when(participants.findWaitingParticipants(CHALLENGE)).thenReturn(List.of(new WaitingParticipant(101, 2L)));
		RescheduledChallenge first = service.reschedule(HOST, CHALLENGE, command(NEW_START, NEW_END));

		RescheduledChallenge again = service.reschedule(HOST, CHALLENGE, command(NEW_START, NEW_END));

		assertEquals(first, again);
		verify(notice).scheduleReset(2L, CHALLENGE, 101, "아침 독서", NEW_START, NEW_END);
		verify(participants).findWaitingParticipants(anyLong());
	}

	@Test
	void recruitingChallengeWithDifferentPeriodIsStillRejected() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD);
		returns(challenge);
		service.reschedule(HOST, CHALLENGE, command(NEW_START, NEW_END));

		BusinessException error = assertThrows(BusinessException.class,
				() -> service.reschedule(HOST, CHALLENGE, command(NEW_START.plusDays(1), NEW_END.plusDays(1))));

		assertEquals(ChallengeErrorCode.NOT_START_HELD, error.getErrorCode());
		assertEquals(NEW_START, challenge.getStartDate());
	}

	@Test
	void startDateTodayOrEarlierIsRejectedAndNothingChanges() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD);
		returns(challenge);

		BusinessException error = assertThrows(BusinessException.class,
				() -> service.reschedule(HOST, CHALLENGE, command(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 24))));

		assertEquals(ChallengeErrorCode.INVALID_START_DATE, error.getErrorCode());
		assertEquals(ChallengeStatus.START_HELD, challenge.getStatus());
		verify(notice, never()).scheduleReset(anyLong(), anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void periodShorterThanSevenDaysCannotEvenBeBuilt() {
		BusinessException error = assertThrows(BusinessException.class,
				() -> command(NEW_START, NEW_START.plusDays(5)));

		assertEquals(ChallengeErrorCode.INVALID_PERIOD, error.getErrorCode());
	}

	private static RescheduleChallengeCommand command(LocalDate start, LocalDate end) {
		return new RescheduleChallengeCommand(start, end);
	}

	private void returns(Challenge challenge) {
		when(challenges.findByIdForUpdate(CHALLENGE)).thenReturn(Optional.of(challenge));
	}

	private static Challenge challenge(ChallengeStatus status) {
		Challenge challenge = new Challenge(HOST, "아침 독서", ChallengeVisibility.PUBLIC, ChallengeCategory.STUDY, "소개", null,
				"미션", "규칙", null, "123456", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 29), 5, 1_000, null);
		try {
			Field field = Challenge.class.getDeclaredField("status");
			field.setAccessible(true);
			field.set(challenge, status);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return challenge;
	}
}

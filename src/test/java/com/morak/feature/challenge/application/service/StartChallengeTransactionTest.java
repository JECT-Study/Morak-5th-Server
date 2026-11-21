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
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.port.out.ChallengeStartHeldNotice;
import com.morak.feature.challenge.application.service.StartChallengeTransaction.Outcome;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 위키 §4.2 시작 처리, §7 시작·보류 표. 합류 인원은 승인 대기를 뺀 값이 포트에서 온다. */
class StartChallengeTransactionTest {

	private static final long HOST = 1L;
	private static final long CHALLENGE = 10L;
	private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

	private final ChallengeRepository challenges = mock(ChallengeRepository.class);
	private final ChallengeParticipationReader participants = mock(ChallengeParticipationReader.class);
	private final ChallengeStartHeldNotice notice = mock(ChallengeStartHeldNotice.class);
	private final StartChallengeTransaction transaction =
			new StartChallengeTransaction(challenges, participants, Optional.of(notice));

	@Test
	void publicWithThreeJoinedStarts() {
		Challenge challenge = given(ChallengeVisibility.PUBLIC, ChallengeStatus.RECRUITING, NOV_1, 3);

		assertEquals(Outcome.STARTED, transaction.process(CHALLENGE, NOV_1));

		assertEquals(ChallengeStatus.IN_PROGRESS, challenge.getStatus());
		assertEquals(NOV_1, challenge.getStartDate());
		verifyNoInteractions(notice);
	}

	/** 시작 예정 11/1, 인원 미달 → 시작 보류, 방장에게 11/3 23:59까지 안내. */
	@Test
	void publicWithTwoJoinedIsHeldAndHostIsNotified() {
		Challenge challenge = given(ChallengeVisibility.PUBLIC, ChallengeStatus.RECRUITING, NOV_1, 2);

		assertEquals(Outcome.HELD, transaction.process(CHALLENGE, NOV_1));

		assertEquals(ChallengeStatus.START_HELD, challenge.getStatus());
		assertEquals(NOV_1, challenge.getStartDate());
		verify(notice).startHeld(HOST, CHALLENGE, "아침 독서", LocalDate.of(2026, 11, 3), NOV_1);
	}

	@Test
	void privateNeedsTwoMembers() {
		Challenge enough = given(ChallengeVisibility.PRIVATE, ChallengeStatus.RECRUITING, NOV_1, 2);
		assertEquals(Outcome.STARTED, transaction.process(CHALLENGE, NOV_1));
		assertEquals(ChallengeStatus.IN_PROGRESS, enough.getStatus());

		Challenge hostOnly = given(ChallengeVisibility.PRIVATE, ChallengeStatus.RECRUITING, NOV_1, 1);
		assertEquals(Outcome.HELD, transaction.process(CHALLENGE, NOV_1));
		assertEquals(ChallengeStatus.START_HELD, hostOnly.getStatus());
	}

	@Test
	void heldChallengeStillShortStaysHeldWithoutNotice() {
		Challenge challenge = given(ChallengeVisibility.PUBLIC, ChallengeStatus.START_HELD, NOV_1, 2);

		assertEquals(Outcome.UNCHANGED, transaction.process(CHALLENGE, NOV_1.plusDays(1)));

		assertEquals(ChallengeStatus.START_HELD, challenge.getStatus());
		verifyNoInteractions(notice);
	}

	/** 시작 예정 11/4, 11/5 23:59 전에 충족 → 11/6 00:00 시작(1일차 = 11/6). */
	@Test
	void heldChallengeFilledStartsNextDayWithShiftedPeriod() {
		Challenge challenge = given(ChallengeVisibility.PUBLIC, ChallengeStatus.START_HELD, LocalDate.of(2026, 11, 4), 3);

		assertEquals(Outcome.STARTED, transaction.process(CHALLENGE, LocalDate.of(2026, 11, 6)));

		assertEquals(ChallengeStatus.IN_PROGRESS, challenge.getStatus());
		assertEquals(LocalDate.of(2026, 11, 6), challenge.getStartDate());
		assertEquals(LocalDate.of(2026, 11, 12), challenge.getEndDate());
	}

	@Test
	void heldChallengeIsNotStartedOnTheDayItWasHeld() {
		Challenge challenge = given(ChallengeVisibility.PUBLIC, ChallengeStatus.START_HELD, NOV_1, 3);

		assertEquals(Outcome.UNCHANGED, transaction.process(CHALLENGE, NOV_1));

		assertEquals(ChallengeStatus.START_HELD, challenge.getStatus());
		verify(participants, never()).countJoined(anyLong());
	}

	/** 인원 충족이 시작일보다 일찍 → 예정 시작일에 시작. 그 전에는 건드리지 않는다. */
	@Test
	void filledBeforeStartDateWaitsUntilStartDate() {
		Challenge challenge = given(ChallengeVisibility.PUBLIC, ChallengeStatus.RECRUITING, NOV_1, 3);

		assertEquals(Outcome.UNCHANGED, transaction.process(CHALLENGE, NOV_1.minusDays(1)));
		assertEquals(ChallengeStatus.RECRUITING, challenge.getStatus());

		assertEquals(Outcome.STARTED, transaction.process(CHALLENGE, NOV_1));
		assertEquals(NOV_1, challenge.getStartDate());
	}

	@Test
	void changedAfterSelectionIsSkipped() {
		for (ChallengeStatus status : new ChallengeStatus[] {ChallengeStatus.DISCARDED, ChallengeStatus.IN_PROGRESS}) {
			Challenge challenge = given(ChallengeVisibility.PUBLIC, status, NOV_1, 3);

			assertEquals(Outcome.UNCHANGED, transaction.process(CHALLENGE, NOV_1.plusDays(1)));
			assertEquals(status, challenge.getStatus());
		}
		// 재설정으로 시작일이 미래로 바뀐 경우
		given(ChallengeVisibility.PUBLIC, ChallengeStatus.RECRUITING, NOV_1.plusDays(10), 3);
		assertEquals(Outcome.UNCHANGED, transaction.process(CHALLENGE, NOV_1));

		when(challenges.findByIdForUpdate(CHALLENGE)).thenReturn(Optional.empty());
		assertEquals(Outcome.UNCHANGED, transaction.process(CHALLENGE, NOV_1));
		verifyNoInteractions(notice);
	}

	/** 누락 보정: 마감(시작일+2일)이 이미 지났으면 보류만 하고 알림 #4는 보내지 않는다. */
	@Test
	void missedChallengeWhoseDeadlinePassedIsHeldWithoutNotice() {
		Challenge challenge = given(ChallengeVisibility.PUBLIC, ChallengeStatus.RECRUITING, NOV_1, 2);

		assertEquals(Outcome.HELD, transaction.process(CHALLENGE, LocalDate.of(2026, 11, 4)));

		assertEquals(ChallengeStatus.START_HELD, challenge.getStatus());
		verifyNoInteractions(notice);
	}

	/** 누락 보정으로 11/2에 보류한 챌린지는 같은 날 인원이 차고 다시 돌려도 시작하지 않고, 11/3에 시작한다. */
	@Test
	void lateHeldChallengeDoesNotStartOnTheDayItWasHeld() {
		Challenge challenge = given(ChallengeVisibility.PUBLIC, ChallengeStatus.RECRUITING, NOV_1, 2);
		LocalDate nov2 = LocalDate.of(2026, 11, 2);
		assertEquals(Outcome.HELD, transaction.process(CHALLENGE, nov2));
		assertEquals(nov2, challenge.getStartHeldOn());

		when(participants.countJoined(CHALLENGE)).thenReturn(3);
		assertEquals(Outcome.UNCHANGED, transaction.process(CHALLENGE, nov2));
		assertEquals(ChallengeStatus.START_HELD, challenge.getStatus());

		assertEquals(Outcome.STARTED, transaction.process(CHALLENGE, LocalDate.of(2026, 11, 3)));
		assertEquals(LocalDate.of(2026, 11, 3), challenge.getStartDate());
		assertEquals(LocalDate.of(2026, 11, 9), challenge.getEndDate());
	}

	@Test
	void missedChallengeBeforeDeadlineIsHeldWithNotice() {
		given(ChallengeVisibility.PUBLIC, ChallengeStatus.RECRUITING, NOV_1, 2);

		assertEquals(Outcome.HELD, transaction.process(CHALLENGE, LocalDate.of(2026, 11, 3)));

		verify(notice).startHeld(HOST, CHALLENGE, "아침 독서", LocalDate.of(2026, 11, 3), NOV_1);
	}

	@Test
	void missingNoticePortFailsOnlyWhenHolding() {
		StartChallengeTransaction withoutNotice = new StartChallengeTransaction(challenges, participants, Optional.empty());
		given(ChallengeVisibility.PUBLIC, ChallengeStatus.RECRUITING, NOV_1, 3);
		assertEquals(Outcome.STARTED, withoutNotice.process(CHALLENGE, NOV_1));

		given(ChallengeVisibility.PUBLIC, ChallengeStatus.RECRUITING, NOV_1, 2);
		BusinessException error = assertThrows(BusinessException.class, () -> withoutNotice.process(CHALLENGE, NOV_1));
		assertEquals(ChallengeErrorCode.START_INTEGRATION_UNAVAILABLE, error.getErrorCode());
	}

	private Challenge given(ChallengeVisibility visibility, ChallengeStatus status, LocalDate start, int joined) {
		Challenge challenge = new Challenge(HOST, "아침 독서", visibility, ChallengeCategory.STUDY, "소개", null,
				"미션", "규칙", null, "123456", start, start.plusDays(6), 5, 0, null);
		try {
			Field field = Challenge.class.getDeclaredField("status");
			field.setAccessible(true);
			field.set(challenge, status);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		when(challenges.findByIdForUpdate(CHALLENGE)).thenReturn(Optional.of(challenge));
		when(participants.countJoined(CHALLENGE)).thenReturn(joined);
		return challenge;
	}
}

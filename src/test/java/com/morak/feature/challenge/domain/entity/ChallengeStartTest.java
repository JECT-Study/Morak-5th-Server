package com.morak.feature.challenge.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.lang.reflect.Field;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 위키 §4.2 시작 처리, §7 시작·보류 표. */
class ChallengeStartTest {

	@Test
	void startOnScheduledDayKeepsDates() {
		Challenge challenge = challenge(ChallengeStatus.RECRUITING, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));

		challenge.start(LocalDate.of(2026, 11, 1));

		assertEquals(ChallengeStatus.IN_PROGRESS, challenge.getStatus());
		assertEquals(LocalDate.of(2026, 11, 1), challenge.getStartDate());
		assertEquals(LocalDate.of(2026, 11, 7), challenge.getEndDate());
	}

	/** 시작 예정 11/4, 11/5 23:59 전에 충족 → 11/6 00:00 시작(1일차 = 11/6). 기간 길이는 유지한다(D-10). */
	@Test
	void startDuringExtraRecruitingShiftsPeriodKeepingLength() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 10));

		challenge.start(LocalDate.of(2026, 11, 6));

		assertEquals(ChallengeStatus.IN_PROGRESS, challenge.getStatus());
		assertEquals(LocalDate.of(2026, 11, 6), challenge.getStartDate());
		assertEquals(LocalDate.of(2026, 11, 12), challenge.getEndDate());
	}

	@Test
	void missedRecruitingChallengeStartsTodayKeepingLength() {
		Challenge challenge = challenge(ChallengeStatus.RECRUITING, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14));

		challenge.start(LocalDate.of(2026, 11, 3));

		assertEquals(LocalDate.of(2026, 11, 3), challenge.getStartDate());
		assertEquals(LocalDate.of(2026, 11, 16), challenge.getEndDate());
	}

	@Test
	void startDueDependsOnStatusAndDate() {
		LocalDate start = LocalDate.of(2026, 11, 4);
		assertFalse(challenge(ChallengeStatus.RECRUITING, start, start.plusDays(6)).isStartDue(start.minusDays(1)));
		assertTrue(challenge(ChallengeStatus.RECRUITING, start, start.plusDays(6)).isStartDue(start));
		assertTrue(challenge(ChallengeStatus.RECRUITING, start, start.plusDays(6)).isStartDue(start.plusDays(1)));
		// 보류된 당일에는 시작하지 않고, 다음 날부터 시작할 수 있다.
		assertFalse(challenge(ChallengeStatus.START_HELD, start, start.plusDays(6)).isStartDue(start));
		assertTrue(challenge(ChallengeStatus.START_HELD, start, start.plusDays(6)).isStartDue(start.plusDays(1)));
		for (ChallengeStatus status : new ChallengeStatus[] {
				ChallengeStatus.IN_PROGRESS, ChallengeStatus.FINALIZING, ChallengeStatus.ENDED, ChallengeStatus.DISCARDED}) {
			assertFalse(challenge(status, start, start.plusDays(6)).isStartDue(start.plusDays(1)));
		}
	}

	@Test
	void startIsRejectedWhenNotDueAndNothingChanges() {
		Challenge future = challenge(ChallengeStatus.RECRUITING, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 10));
		Challenge heldToday = challenge(ChallengeStatus.START_HELD, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 10));
		Challenge discarded = challenge(ChallengeStatus.DISCARDED, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 10));

		for (Challenge challenge : new Challenge[] {future, heldToday, discarded}) {
			ChallengeStatus before = challenge.getStatus();
			LocalDate day = challenge == future ? LocalDate.of(2026, 11, 3) : LocalDate.of(2026, 11, 4);
			BusinessException error = assertThrows(BusinessException.class, () -> challenge.start(day));

			assertEquals(ChallengeErrorCode.NOT_STARTABLE, error.getErrorCode());
			assertEquals(before, challenge.getStatus());
			assertEquals(LocalDate.of(2026, 11, 4), challenge.getStartDate());
		}
	}

	@Test
	void onlyRecruitingCanBeHeld() {
		Challenge challenge = challenge(ChallengeStatus.RECRUITING, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));

		challenge.holdStart(LocalDate.of(2026, 11, 1));

		assertEquals(ChallengeStatus.START_HELD, challenge.getStatus());
		assertEquals(LocalDate.of(2026, 11, 1), challenge.getStartDate());
		assertEquals(LocalDate.of(2026, 11, 1), challenge.getStartHeldOn());
		assertEquals(1, challenge.getCurrentCount());
		for (ChallengeStatus status : ChallengeStatus.values()) {
			if (status == ChallengeStatus.RECRUITING) {
				continue;
			}
			Challenge other = challenge(status, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));
			BusinessException error = assertThrows(BusinessException.class, () -> other.holdStart(LocalDate.of(2026, 11, 1)));
			assertEquals(ChallengeErrorCode.NOT_STARTABLE, error.getErrorCode());
			assertEquals(status, other.getStatus());
		}
	}

	/**
	 * 누락 보정: 예정 시작일 11/1의 배치를 놓쳐 11/2에 보류하면, 시작일은 11/1 그대로지만 보류한 날(11/2)에는 시작하지 않고
	 * 11/3부터 시작할 수 있다. 추가 모집 마감은 예정 시작일 기준(11/3)이다.
	 */
	@Test
	void lateHoldWaitsUntilDayAfterActualHold() {
		Challenge challenge = challenge(ChallengeStatus.RECRUITING, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));

		challenge.holdStart(LocalDate.of(2026, 11, 2));

		assertEquals(LocalDate.of(2026, 11, 2), challenge.getStartHeldOn());
		assertFalse(challenge.isStartDue(LocalDate.of(2026, 11, 2)));
		assertTrue(challenge.isStartDue(LocalDate.of(2026, 11, 3)));
		assertEquals(LocalDate.of(2026, 11, 3), challenge.recruitDeadlineDate());
		BusinessException error = assertThrows(BusinessException.class, () -> challenge.start(LocalDate.of(2026, 11, 2)));
		assertEquals(ChallengeErrorCode.NOT_STARTABLE, error.getErrorCode());
	}

	@Test
	void rescheduleClearsHoldDay() {
		Challenge challenge = challenge(ChallengeStatus.RECRUITING, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));
		challenge.holdStart(LocalDate.of(2026, 11, 1));

		challenge.reschedule(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 7), java.time.Instant.parse("2026-11-02T00:00:00Z"));

		assertEquals(null, challenge.getStartHeldOn());
	}

	/** 시작 예정 11/1, 인원 미달 → 11/3 23:59까지 추가 모집. */
	@Test
	void recruitDeadlineIsStartDatePlusTwoDays() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));

		assertEquals(LocalDate.of(2026, 11, 3), challenge.recruitDeadlineDate());
	}

	@Test
	void recruitDeadlineEndsAtMidnightKstAfterDeadlineDay() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));

		// 11/3 23:59:59 KST까지가 추가 모집이고, 11/4 00:00 KST(= 11/3 15:00 UTC)에 끝난다.
		assertEquals(java.time.Instant.parse("2026-11-03T15:00:00Z"), challenge.recruitDeadlineEnd());
	}

	@Test
	void minimumMembersArePublicThreeAndPrivateTwo() {
		Challenge publicChallenge = challenge(ChallengeStatus.RECRUITING, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));
		Challenge privateChallenge = challenge(ChallengeVisibility.PRIVATE, ChallengeStatus.RECRUITING,
				LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));

		assertFalse(publicChallenge.hasEnoughMembers(2));
		assertTrue(publicChallenge.hasEnoughMembers(3));
		assertFalse(privateChallenge.hasEnoughMembers(1));
		assertTrue(privateChallenge.hasEnoughMembers(2));
	}

	private static Challenge challenge(ChallengeStatus status, LocalDate start, LocalDate end) {
		return challenge(ChallengeVisibility.PUBLIC, status, start, end);
	}

	private static Challenge challenge(ChallengeVisibility visibility, ChallengeStatus status, LocalDate start, LocalDate end) {
		Challenge challenge = new Challenge(1L, "아침 독서", visibility, ChallengeCategory.STUDY, "소개", null,
				"미션", "규칙", null, "123456", start, end, 5, 0, null);
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

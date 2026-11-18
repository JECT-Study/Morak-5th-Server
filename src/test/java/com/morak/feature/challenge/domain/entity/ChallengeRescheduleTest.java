package com.morak.feature.challenge.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.lang.reflect.Field;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ChallengeRescheduleTest {

	private static final LocalDate NEW_START = LocalDate.of(2026, 12, 20);
	private static final LocalDate NEW_END = LocalDate.of(2026, 12, 26);

	@Test
	void startHeldChallengeReturnsToRecruitingWithNewPeriod() {
		Challenge challenge = challengeWith(ChallengeStatus.START_HELD);

		challenge.reschedule(NEW_START, NEW_END);

		assertEquals(ChallengeStatus.RECRUITING, challenge.getStatus());
		assertEquals(NEW_START, challenge.getStartDate());
		assertEquals(NEW_END, challenge.getEndDate());
		assertEquals(1, challenge.getCurrentCount());
		assertEquals(5, challenge.getCapacity());
		assertEquals(1_000, challenge.getPointAmount());
	}

	@Test
	void onlyStartHeldChallengeCanBeRescheduled() {
		for (ChallengeStatus status : ChallengeStatus.values()) {
			if (status == ChallengeStatus.START_HELD) {
				continue;
			}
			Challenge challenge = challengeWith(status);

			BusinessException error = assertThrows(BusinessException.class,
					() -> challenge.reschedule(NEW_START, NEW_END));

			assertEquals(ChallengeErrorCode.NOT_START_HELD, error.getErrorCode());
			assertEquals(status, challenge.getStatus());
			assertEquals(LocalDate.of(2026, 12, 1), challenge.getStartDate());
			assertEquals(LocalDate.of(2026, 12, 14), challenge.getEndDate());
		}
	}

	private static Challenge challengeWith(ChallengeStatus status) {
		Challenge challenge = new Challenge(1L, "아침 독서", ChallengeVisibility.PUBLIC, ChallengeCategory.STUDY, "소개", null,
				"미션", "규칙", null, "123456", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 14), 5, 1_000, null);
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

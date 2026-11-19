package com.morak.feature.challenge.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class ChallengeDiscardTest {

	@Test
	void startHeldChallengeIsDiscarded() {
		Challenge challenge = challengeWith(ChallengeStatus.START_HELD);

		challenge.discard();

		assertEquals(ChallengeStatus.DISCARDED, challenge.getStatus());
		assertTrue(challenge.isDiscarded());
		assertEquals(1, challenge.getCurrentCount());
	}

	@Test
	void onlyStartHeldChallengeCanBeDiscarded() {
		for (ChallengeStatus status : ChallengeStatus.values()) {
			if (status == ChallengeStatus.START_HELD) {
				continue;
			}
			Challenge challenge = challengeWith(status);

			BusinessException error = assertThrows(BusinessException.class, challenge::discard);

			assertEquals(ChallengeErrorCode.NOT_START_HELD, error.getErrorCode());
			assertEquals(status, challenge.getStatus());
			assertEquals(status == ChallengeStatus.DISCARDED, challenge.isDiscarded());
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

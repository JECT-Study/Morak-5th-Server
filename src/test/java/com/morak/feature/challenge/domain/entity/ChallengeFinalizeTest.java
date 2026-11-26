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

/** 위키 §4.2 상태·§4.5 결과 확정: IN_PROGRESS → FINALIZING(종료일 경과) → ENDED. 7일 챌린지(11/20~11/26). */
class ChallengeFinalizeTest {

	private static final LocalDate DAY_AFTER_END = LocalDate.of(2026, 11, 27);

	@Test
	void inProgressChallengeWaitsForResultsAfterTheEndDateAndThenEnds() {
		Challenge challenge = challenge(ChallengeStatus.IN_PROGRESS);

		challenge.beginFinalizing(DAY_AFTER_END);
		assertEquals(ChallengeStatus.FINALIZING, challenge.getStatus());

		challenge.end();
		assertEquals(ChallengeStatus.ENDED, challenge.getStatus());
	}

	@Test
	void cannotBeginFinalizingOnOrBeforeTheEndDate() {
		Challenge challenge = challenge(ChallengeStatus.IN_PROGRESS);

		assertRejected(() -> challenge.beginFinalizing(LocalDate.of(2026, 11, 26)));
		assertEquals(ChallengeStatus.IN_PROGRESS, challenge.getStatus());
	}

	@Test
	void onlyInProgressCanBeginFinalizingAndOnlyFinalizingCanEnd() {
		assertRejected(() -> challenge(ChallengeStatus.ENDED).beginFinalizing(DAY_AFTER_END));
		assertRejected(() -> challenge(ChallengeStatus.DISCARDED).beginFinalizing(DAY_AFTER_END));
		assertRejected(() -> challenge(ChallengeStatus.IN_PROGRESS).end());
		assertRejected(() -> challenge(ChallengeStatus.ENDED).end());
	}

	private static void assertRejected(org.junit.jupiter.api.function.Executable action) {
		assertEquals(ChallengeErrorCode.NOT_FINALIZABLE,
				assertThrows(BusinessException.class, action).getErrorCode());
	}

	private static Challenge challenge(ChallengeStatus status) {
		Challenge challenge = new Challenge(1L, "아침 독서", ChallengeVisibility.PUBLIC, ChallengeCategory.STUDY, "소개",
				null, "미션", "규칙", null, "123456", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 26), 5, 0, null);
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

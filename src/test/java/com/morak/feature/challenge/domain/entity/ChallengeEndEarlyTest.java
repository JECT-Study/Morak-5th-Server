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

/** 위키 §4.4 조기 종료: 종료일을 오늘로 바꾸고 FINALIZING으로 둔다. */
class ChallengeEndEarlyTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

	@Test
	void inProgressChallengeEndsTodayAndWaitsForResults() {
		Challenge challenge = challenge(ChallengeStatus.IN_PROGRESS);

		challenge.endEarly(TODAY);

		assertEquals(ChallengeStatus.FINALIZING, challenge.getStatus());
		assertEquals(TODAY, challenge.getEndDate());
	}

	@Test
	void rejectsChallengeThatIsNotInProgress() {
		Challenge challenge = challenge(ChallengeStatus.FINALIZING);

		BusinessException error = assertThrows(BusinessException.class, () -> challenge.endEarly(TODAY));

		assertEquals(ChallengeErrorCode.NOT_IN_PROGRESS, error.getErrorCode());
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

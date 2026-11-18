package com.morak.feature.challenge.domain.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ChallengePeriodTest {

	private static final LocalDate START = LocalDate.of(2026, 12, 1);

	@Test
	void sevenDaysInclusiveIsAllowedAndSixDaysIsNot() {
		assertDoesNotThrow(() -> ChallengePeriod.validate(START, START.plusDays(6)));

		BusinessException error = assertThrows(BusinessException.class,
				() -> ChallengePeriod.validate(START, START.plusDays(5)));

		assertEquals(ChallengeErrorCode.INVALID_PERIOD, error.getErrorCode());
	}

	@Test
	void endBeforeStartIsInvalidPeriod() {
		BusinessException error = assertThrows(BusinessException.class,
				() -> ChallengePeriod.validate(START, START.minusDays(1)));

		assertEquals(ChallengeErrorCode.INVALID_PERIOD, error.getErrorCode());
	}

	@Test
	void datesOutsideStorageRangeAreRejected() {
		BusinessException error = assertThrows(BusinessException.class,
				() -> ChallengePeriod.validate(START, LocalDate.of(6_000_000, 1, 1)));

		assertEquals(ChallengeErrorCode.DATE_OUT_OF_RANGE, error.getErrorCode());
	}

	@Test
	void startDateMustBeAfterTodayInKst() {
		// 2026-11-18 14:59:59 UTC = KST 23:59:59 (오늘은 11/18)
		Instant beforeKstMidnight = Instant.parse("2026-11-18T14:59:59Z");
		// 2026-11-18 15:00:00 UTC = KST 11/19 00:00:00
		Instant atKstMidnight = Instant.parse("2026-11-18T15:00:00Z");

		assertDoesNotThrow(() -> ChallengePeriod.validateStartDate(LocalDate.of(2026, 11, 19), beforeKstMidnight));
		BusinessException today = assertThrows(BusinessException.class,
				() -> ChallengePeriod.validateStartDate(LocalDate.of(2026, 11, 18), beforeKstMidnight));
		assertEquals(ChallengeErrorCode.INVALID_START_DATE, today.getErrorCode());
		BusinessException nextDay = assertThrows(BusinessException.class,
				() -> ChallengePeriod.validateStartDate(LocalDate.of(2026, 11, 19), atKstMidnight));
		assertEquals(ChallengeErrorCode.INVALID_START_DATE, nextDay.getErrorCode());
		assertDoesNotThrow(() -> ChallengePeriod.validateStartDate(LocalDate.of(2026, 11, 20), atKstMidnight));
	}
}

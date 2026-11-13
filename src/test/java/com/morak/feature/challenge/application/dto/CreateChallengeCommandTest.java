package com.morak.feature.challenge.application.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CreateChallengeCommandTest {

	private static final LocalDate START = LocalDate.of(2026, 12, 1);

	@Test
	void requiresSevenDaysIncludingBothDates() {
		assertError(ChallengeErrorCode.INVALID_PERIOD, ChallengeVisibility.PUBLIC, 3, 0, 6);
		assertDoesNotThrow(() -> command(ChallengeVisibility.PUBLIC, 3, 0, 7));
	}

	@Test
	void rejectsDatesOutsidePostgresDateRange() {
		LocalDate tooLate = LocalDate.of(5874898, 1, 1);
		BusinessException invalidEnd = assertThrows(BusinessException.class,
				() -> command(START, tooLate, ChallengeVisibility.PUBLIC, 3, 0));
		assertEquals(ChallengeErrorCode.DATE_OUT_OF_RANGE, invalidEnd.getErrorCode());
		BusinessException invalidStart = assertThrows(BusinessException.class,
				() -> command(LocalDate.MIN, START, ChallengeVisibility.PUBLIC, 3, 0));
		assertEquals(ChallengeErrorCode.DATE_OUT_OF_RANGE, invalidStart.getErrorCode());
		assertDoesNotThrow(() -> command(START, tooLate.minusDays(1), ChallengeVisibility.PUBLIC, 3, 0));
	}

	@ParameterizedTest
	@CsvSource({"PUBLIC,2", "PUBLIC,11", "PRIVATE,1", "PRIVATE,11"})
	void rejectsCapacityOutsideVisibilityRange(ChallengeVisibility visibility, int capacity) {
		assertError(ChallengeErrorCode.INVALID_CAPACITY, visibility, capacity, 0, 7);
	}

	@ParameterizedTest
	@CsvSource({"PUBLIC,3", "PUBLIC,10", "PRIVATE,2", "PRIVATE,10"})
	void acceptsCapacityBoundaries(ChallengeVisibility visibility, int capacity) {
		assertDoesNotThrow(() -> command(visibility, capacity, 0, 7));
	}

	@ParameterizedTest
	@CsvSource({"PUBLIC,999", "PUBLIC,5001", "PRIVATE,1000", "PUBLIC,-1"})
	void rejectsInvalidPoints(ChallengeVisibility visibility, int amount) {
		assertError(ChallengeErrorCode.INVALID_POINT_AMOUNT, visibility, 3, amount, 7);
	}

	@ParameterizedTest
	@CsvSource({"PUBLIC,0", "PUBLIC,1000", "PUBLIC,5000", "PRIVATE,0"})
	void acceptsValidPoints(ChallengeVisibility visibility, int amount) {
		assertDoesNotThrow(() -> command(visibility, 3, amount, 7));
	}

	private void assertError(ChallengeErrorCode expected, ChallengeVisibility visibility, int capacity, int amount, int days) {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> command(visibility, capacity, amount, days));
		assertEquals(expected, exception.getErrorCode());
	}

	private CreateChallengeCommand command(ChallengeVisibility visibility, int capacity, int amount, int days) {
		return command(START, START.plusDays(days - 1), visibility, capacity, amount);
	}

	private CreateChallengeCommand command(LocalDate start, LocalDate end, ChallengeVisibility visibility, int capacity, int amount) {
		return new CreateChallengeCommand("아침 독서", visibility, ChallengeCategory.READING, "한줄 소개", "독서 습관 만들기", "매일 10쪽 읽기",
				"읽은 쪽 사진 올리기", start, end, capacity, amount, null);
	}
}

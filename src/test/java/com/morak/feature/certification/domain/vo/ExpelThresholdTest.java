package com.morak.feature.certification.domain.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 위키 §7 퇴장 기준. */
class ExpelThresholdTest {

	@ParameterizedTest
	@CsvSource({"7, 2", "14, 3", "30, 5", "35, 6"})
	void ceilsFifteenPercent(int days, int expected) {
		assertEquals(expected, ExpelThreshold.of(days));
	}

	@Test
	void countsBothEndsOfThePeriod() {
		assertEquals(2, ExpelThreshold.of(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 26)));
	}
}

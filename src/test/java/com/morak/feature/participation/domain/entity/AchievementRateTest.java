package com.morak.feature.participation.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 위키 §7 개인 인증률 케이스와 소수 첫째 자리 half-up 반올림을 검증한다. */
class AchievementRateTest {

	@Test
	void twentyOfTwentyFiveIsEighty() {
		assertEquals(new BigDecimal("80.00"), AchievementRate.percent(20, 25));
	}

	@Test
	void sumsAcrossChallengesInsteadOfAveraging() {
		// 의무 15+10, 합계 승인 20 → 평균이 아니라 합계로 80%
		assertEquals(new BigDecimal("80.00"), AchievementRate.percent(20, 25));
	}

	@Test
	void boundaryValuesAreExact() {
		assertEquals(new BigDecimal("30.00"), AchievementRate.percent(3, 10));
		assertEquals(new BigDecimal("60.00"), AchievementRate.percent(6, 10));
		assertEquals(new BigDecimal("85.00"), AchievementRate.percent(17, 20));
		assertEquals(new BigDecimal("95.00"), AchievementRate.percent(19, 20));
	}

	@Test
	void fullAndZeroAchievement() {
		assertEquals(new BigDecimal("100.00"), AchievementRate.percent(70, 70));
		assertEquals(new BigDecimal("0.00"), AchievementRate.percent(0, 70));
	}

	@Test
	void roundsHalfUpAtFirstDecimalPlace() {
		// 90/105 = 85.714...% → 85.7
		assertEquals(new BigDecimal("85.70"), AchievementRate.percent(90, 105));
		// 1/16 = 6.25% → 6.3 (정확히 절반은 올림)
		assertEquals(new BigDecimal("6.30"), AchievementRate.percent(1, 16));
		// 1/3 = 33.333...% → 33.3
		assertEquals(new BigDecimal("33.30"), AchievementRate.percent(1, 3));
	}

	@Test
	void noTargetCountIsZeroRate() {
		assertEquals(new BigDecimal("0.00"), AchievementRate.percent(0, 0));
	}
}

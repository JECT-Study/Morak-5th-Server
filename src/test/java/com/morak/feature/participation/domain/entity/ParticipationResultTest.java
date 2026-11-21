package com.morak.feature.participation.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ParticipationResultTest {

	private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

	@Test
	void calculatesRateAndStoresFinalizedTimeInUtc() {
		ParticipationResult result = ParticipationResult.of(1L, 10L, 100L, 20, 25, 3, 7, true, NOW);

		assertEquals(1L, result.getParticipationId());
		assertEquals(10L, result.getChallengeId());
		assertEquals(100L, result.getUserId());
		assertEquals(20, result.getApprovedCount());
		assertEquals(25, result.getTotalCount());
		assertEquals(new BigDecimal("80.00"), result.getAchievementRate());
		assertEquals(3, result.getCurrentStreak());
		assertEquals(7, result.getMaxStreak());
		assertTrue(result.isCompleted());
		assertEquals(LocalDateTime.of(2026, 11, 20, 3, 0), result.getFinalizedAt());
	}

	@Test
	void rejectsApprovedCountAboveTotal() {
		assertThrows(IllegalArgumentException.class,
				() -> ParticipationResult.of(1L, 10L, 100L, 26, 25, 0, 0, false, NOW));
	}

	@Test
	void rejectsNegativeCounts() {
		assertThrows(IllegalArgumentException.class,
				() -> ParticipationResult.of(1L, 10L, 100L, -1, 25, 0, 0, false, NOW));
		assertThrows(IllegalArgumentException.class,
				() -> ParticipationResult.of(1L, 10L, 100L, 0, 25, -1, 0, false, NOW));
	}

	@Test
	void rejectsCurrentStreakAboveMaxStreak() {
		assertThrows(IllegalArgumentException.class,
				() -> ParticipationResult.of(1L, 10L, 100L, 10, 25, 5, 4, false, NOW));
	}
}

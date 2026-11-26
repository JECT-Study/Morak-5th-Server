package com.morak.feature.certification.domain.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CertificationTallyTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 20);

	private static LocalDate day(int dayOfMonth) {
		return LocalDate.of(2026, 11, dayOfMonth);
	}

	@Test
	void allSuccessIsOneStreakOverTheWholePeriod() {
		assertEquals(new CertificationTally(7, 7, 7, 7),
				CertificationTally.of(START, day(26), Set.of(day(20), day(21), day(22), day(23), day(24), day(25), day(26))));
	}

	/** TODO(D-47): 실패한 날에 연속이 끊긴다. */
	@Test
	void failureBreaksTheStreak() {
		assertEquals(new CertificationTally(6, 7, 3, 3),
				CertificationTally.of(START, day(26), Set.of(day(20), day(21), day(22), day(24), day(25), day(26))));
		assertEquals(new CertificationTally(4, 7, 0, 3),
				CertificationTally.of(START, day(26), Set.of(day(20), day(21), day(22), day(25))));
	}

	@Test
	void successOutsideTheObligationIsNotCounted() {
		assertEquals(new CertificationTally(2, 2, 2, 2),
				CertificationTally.of(START, day(21), Set.of(day(20), day(21), day(22))));
	}

	@Test
	void expelledOnTheStartDateHasNoObligation() {
		assertEquals(new CertificationTally(0, 0, 0, 0), CertificationTally.of(START, day(19), Set.of(day(20))));
	}
}

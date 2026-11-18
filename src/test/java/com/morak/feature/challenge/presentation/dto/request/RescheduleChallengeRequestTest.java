package com.morak.feature.challenge.presentation.dto.request;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Validation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RescheduleChallengeRequestTest {

	@Test
	void bothDatesAreRequired() {
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			var validator = factory.getValidator();

			assertEquals(0, validator.validate(new RescheduleChallengeRequest(
					LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 7))).size());
			assertEquals(1, validator.validate(new RescheduleChallengeRequest(null, LocalDate.of(2026, 12, 7))).size());
			assertEquals(2, validator.validate(new RescheduleChallengeRequest(null, null)).size());
		}
	}
}

package com.morak.feature.challenge.presentation.dto.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CreateChallengeRequestTest {

	@Test
	void titleAllowsAtMostTwelveCharacters() {
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			var validator = factory.getValidator();
			assertTrue(validator.validate(request("가".repeat(12), null)).isEmpty());
			var violations = validator.validate(request("가".repeat(13), null));
			assertEquals(1, violations.size());
			assertEquals("title", violations.iterator().next().getPropertyPath().toString());
		}
	}

	@Test
	void requiresDescriptionAndAllowsMissingPurpose() {
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			var validator = factory.getValidator();
			var valid = request("독서", "매일 함께 책 읽어요", null);
			assertTrue(validator.validate(valid).isEmpty());
			assertEquals("매일 함께 책 읽어요", valid.toCommand().description());
			assertNull(valid.toCommand().purpose());

			var violations = validator.validate(request("독서", " ", null));
			assertEquals(1, violations.size());
			assertEquals("description", violations.iterator().next().getPropertyPath().toString());
		}
	}

	@Test
	void fractionalPointsAreNotRoundedToAnInteger() {
		var exception = assertThrows(BusinessException.class,
				() -> request("독서", new BigDecimal("1000.5")).toCommand());
		assertEquals(ChallengeErrorCode.INVALID_POINT_AMOUNT, exception.getErrorCode());
	}

	private CreateChallengeRequest request(String title, BigDecimal points) {
		return request(title, "한줄 소개", points);
	}

	private CreateChallengeRequest request(String title, String description, BigDecimal points) {
		LocalDate start = LocalDate.of(2026, 12, 1);
		return new CreateChallengeRequest(title, ChallengeVisibility.PUBLIC, ChallengeCategory.READING,
				description, null, "미션", "인증",
				start, start.plusDays(6), BigDecimal.valueOf(3), points, null);
	}
}

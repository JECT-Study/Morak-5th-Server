package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.domain.entity.Challenge.Category;
import com.morak.feature.challenge.domain.entity.Challenge.Visibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CreateChallengeServiceTest {

	private final CreateChallengeTransaction transaction = mock(CreateChallengeTransaction.class);
	private final CreateChallengeService service = new CreateChallengeService(transaction,
			Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC));

	@Test
	void rejectsTodayUsingKst() {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> service.create(1L, command(LocalDate.of(2026, 11, 10))));
		assertEquals(ChallengeErrorCode.INVALID_START_DATE, exception.getErrorCode());
		verifyNoInteractions(transaction);
	}

	@Test
	void retriesAnInviteCodeCollisionAndReturnsSixDigits() {
		CreateChallengeCommand command = command(LocalDate.of(2026, 11, 11));
		var collision = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"),
				"uk_challenge_invite_code");
		when(transaction.create(eq(1L), eq(command), anyString()))
				.thenThrow(new DataIntegrityViolationException("duplicate", collision))
				.thenReturn(12L);

		var created = service.create(1L, command);

		assertEquals(12L, created.challengeId());
		assertTrue(created.inviteCode().matches("[0-9]{6}"));
	}

	private CreateChallengeCommand command(LocalDate start) {
		return new CreateChallengeCommand("독서", Visibility.PRIVATE, Category.READING, "목적", "미션", "인증",
				start, start.plusDays(6), 2, 0, null);
	}
}

package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.application.port.out.ChallengeOwnerLock;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.challenge.application.port.out.InvalidatedApplicationNotice;
import com.morak.feature.challenge.domain.entity.Challenge.Category;
import com.morak.feature.challenge.domain.entity.Challenge.Visibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
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
		when(transaction.create(eq(1L), eq(command), anyString()))
				.thenThrow(duplicateKey("uk_challenge_invite_code"))
				.thenReturn(12L);

		var created = service.create(1L, command);

		assertEquals(12L, created.challengeId());
		assertTrue(created.inviteCode().matches("[0-9]{6}"));
	}

	@Test
	void stopsAfterFiveInviteCodeCollisions() {
		CreateChallengeCommand command = command(LocalDate.of(2026, 11, 11));
		when(transaction.create(eq(1L), eq(command), anyString()))
				.thenThrow(duplicateKey("uk_challenge_invite_code"));

		BusinessException error = assertThrows(BusinessException.class, () -> service.create(1L, command));

		assertEquals(ChallengeErrorCode.INVITE_CODE_UNAVAILABLE, error.getErrorCode());
		verify(transaction, times(5)).create(eq(1L), eq(command), anyString());
	}

	@Test
	void doesNotRetryUnrelatedIntegrityViolation() {
		CreateChallengeCommand command = command(LocalDate.of(2026, 11, 11));
		var violation = duplicateKey("uk_participation_challenge_user");
		when(transaction.create(eq(1L), eq(command), anyString())).thenThrow(violation);

		assertSame(violation, assertThrows(DataIntegrityViolationException.class, () -> service.create(1L, command)));
		verify(transaction).create(eq(1L), eq(command), anyString());
	}

	@Test
	void rejectsStartDateThatBecomesTodayWhileWaitingForOwnerLock() {
		Clock clock = mock(Clock.class);
		when(clock.instant()).thenReturn(Instant.parse("2026-11-09T14:59:59Z"));
		ChallengeOwnerLock owners = mock(ChallengeOwnerLock.class);
		doAnswer(invocation -> {
			when(clock.instant()).thenReturn(Instant.parse("2026-11-09T15:00:00Z"));
			return null;
		}).when(owners).lock(1L);
		ChallengeRepository challenges = mock(ChallengeRepository.class);
		var transactionalCreator = new CreateChallengeTransaction(challenges, Optional.of(mock(ChallengeParticipation.class)),
				owners, Optional.of(mock(InvalidatedApplicationNotice.class)), clock);
		var creator = new CreateChallengeService(transactionalCreator, clock);

		BusinessException error = assertThrows(BusinessException.class,
				() -> creator.create(1L, command(LocalDate.of(2026, 11, 10))));

		assertEquals(ChallengeErrorCode.INVALID_START_DATE, error.getErrorCode());
		verify(owners).lock(1L);
		verifyNoInteractions(challenges);
	}

	@Test
	void rejectsCreationBeforeWritingIfIntegrationIsUnavailable() {
		ChallengeRepository challenges = mock(ChallengeRepository.class);
		ChallengeOwnerLock owners = mock(ChallengeOwnerLock.class);
		var creator = new CreateChallengeTransaction(challenges, Optional.empty(), owners, Optional.empty(),
				Clock.systemUTC());

		BusinessException error = assertThrows(BusinessException.class,
				() -> creator.create(1L, command(LocalDate.of(2026, 11, 11)), "000001"));

		assertEquals(ChallengeErrorCode.CREATION_INTEGRATION_UNAVAILABLE, error.getErrorCode());
		verifyNoInteractions(challenges, owners);
	}

	private DataIntegrityViolationException duplicateKey(String constraintName) {
		var violation = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"),
				constraintName);
		return new DataIntegrityViolationException("duplicate", violation);
	}

	private CreateChallengeCommand command(LocalDate start) {
		return new CreateChallengeCommand("독서", Visibility.PRIVATE, Category.READING, "목적", "미션", "인증",
				start, start.plusDays(6), 2, 0, null);
	}
}

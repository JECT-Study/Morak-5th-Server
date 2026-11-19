package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.dto.result.DiscardedChallenge;
import com.morak.feature.challenge.application.port.out.ChallengeDiscardNotice;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.DiscardedParticipant;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class DiscardChallengeServiceTest {

	private static final long HOST = 1L;
	private static final long CHALLENGE = 10L;
	private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");

	private final ChallengeRepository challenges = mock(ChallengeRepository.class);
	private final ChallengeParticipation participation = mock(ChallengeParticipation.class);
	private final ChallengeDiscardNotice notice = mock(ChallengeDiscardNotice.class);
	private final DiscardChallengeTransaction transaction = new DiscardChallengeTransaction(
			challenges, Optional.of(participation), Clock.fixed(NOW, ZoneOffset.UTC));
	private final DiscardChallengeService service = new DiscardChallengeService(
			transaction, Optional.of(notice), mock(PlatformTransactionManager.class));

	@Test
	void discardsRefundsEveryoneAndNotifiesEveryoneExceptHost() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD, 1_000);
		returns(challenge);
		when(participation.lockPointAccountsForDiscard(CHALLENGE)).thenReturn(Set.of(1L, 2L, 3L));
		when(participation.discardAll(CHALLENGE, "아침 독서", Set.of(1L, 2L, 3L), NOW)).thenReturn(List.of(
				new DiscardedParticipant(100, 1L, true, 1_000),
				new DiscardedParticipant(101, 2L, false, 1_000),
				new DiscardedParticipant(102, 3L, false, 1_000)));

		DiscardedChallenge result = service.discard(HOST, CHALLENGE);

		assertEquals(new DiscardedChallenge(CHALLENGE, "DISCARDED"), result);
		assertEquals(ChallengeStatus.DISCARDED, challenge.getStatus());
		verify(notice).discardedByHost(2L, CHALLENGE, 101, "아침 독서", 1_000);
		verify(notice).discardedByHost(3L, CHALLENGE, 102, "아침 독서", 1_000);
		verify(notice, never()).discardedByHost(org.mockito.ArgumentMatchers.eq(1L), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	void retriesInNewTransactionWhenApplicationsChangedAndNotifiesOnce() {
		// 트랜잭션이 롤백된 것처럼 매번 새로 읽은 시작 보류 챌린지를 돌려준다.
		when(challenges.findById(CHALLENGE)).thenAnswer(i -> Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		when(challenges.findByIdForUpdate(CHALLENGE))
				.thenAnswer(i -> Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		when(participation.lockPointAccountsForDiscard(CHALLENGE)).thenReturn(Set.of(1L, 2L), Set.of(1L, 2L, 3L));
		when(participation.discardAll(anyLong(), anyString(), any(), any()))
				.thenThrow(new BusinessException(ChallengeErrorCode.DISCARD_APPLICATIONS_CHANGED))
				.thenReturn(List.of(new DiscardedParticipant(100, 1L, true, 1_000),
						new DiscardedParticipant(101, 2L, false, 1_000), new DiscardedParticipant(102, 3L, false, 1_000)));

		DiscardedChallenge result = service.discard(HOST, CHALLENGE);

		assertEquals(new DiscardedChallenge(CHALLENGE, "DISCARDED"), result);
		verify(participation, times(2)).lockPointAccountsForDiscard(CHALLENGE);
		verify(participation).discardAll(CHALLENGE, "아침 독서", Set.of(1L, 2L, 3L), NOW);
		verify(notice).discardedByHost(2L, CHALLENGE, 101, "아침 독서", 1_000);
		verify(notice).discardedByHost(3L, CHALLENGE, 102, "아침 독서", 1_000);
	}

	@Test
	void givesUpWith409AfterMaxAttempts() {
		when(challenges.findById(CHALLENGE)).thenAnswer(i -> Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		when(challenges.findByIdForUpdate(CHALLENGE))
				.thenAnswer(i -> Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		when(participation.discardAll(anyLong(), anyString(), any(), any()))
				.thenThrow(new BusinessException(ChallengeErrorCode.DISCARD_APPLICATIONS_CHANGED));

		BusinessException error = assertThrows(BusinessException.class, () -> service.discard(HOST, CHALLENGE));

		assertEquals(ChallengeErrorCode.DISCARD_APPLICATIONS_CHANGED, error.getErrorCode());
		verify(participation, times(DiscardChallengeService.MAX_ATTEMPTS)).discardAll(anyLong(), anyString(), any(), any());
		verifyNoInteractions(notice);
	}

	@Test
	void otherErrorsAreNotRetried() {
		returns(challenge(ChallengeStatus.RECRUITING, 1_000));

		assertThrows(BusinessException.class, () -> service.discard(HOST, CHALLENGE));

		verify(challenges, times(1)).findById(CHALLENGE);
	}

	@Test
	void challengeWithoutPointsDoesNotLockAnyAccount() {
		returns(challenge(ChallengeStatus.START_HELD, 0));
		when(participation.discardAll(anyLong(), anyString(), any(), any())).thenReturn(List.of(
				new DiscardedParticipant(100, 1L, true, 0), new DiscardedParticipant(101, 2L, false, 0)));

		service.discard(HOST, CHALLENGE);

		verify(participation, never()).lockPointAccountsForDiscard(anyLong());
		verify(notice).discardedByHost(2L, CHALLENGE, 101, "아침 독서", 0);
	}

	@Test
	void alreadyDiscardedReturnsSameResponseWithoutRefundOrNotice() {
		returns(challenge(ChallengeStatus.DISCARDED, 1_000));

		DiscardedChallenge result = service.discard(HOST, CHALLENGE);

		assertEquals(new DiscardedChallenge(CHALLENGE, "DISCARDED"), result);
		verifyNoInteractions(participation, notice);
	}

	@Test
	void onlyOwnerCanDiscard() {
		returns(challenge(ChallengeStatus.START_HELD, 1_000));

		BusinessException error = assertThrows(BusinessException.class, () -> service.discard(2L, CHALLENGE));

		assertEquals(ChallengeErrorCode.NOT_OWNER, error.getErrorCode());
		verifyNoInteractions(participation, notice);
	}

	@Test
	void missingChallengeIsNotFound() {
		when(challenges.findById(CHALLENGE)).thenReturn(Optional.empty());

		BusinessException error = assertThrows(BusinessException.class, () -> service.discard(HOST, CHALLENGE));

		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
	}

	@Test
	void challengeThatIsNotStartHeldCannotBeDiscarded() {
		for (ChallengeStatus status : List.of(ChallengeStatus.RECRUITING, ChallengeStatus.IN_PROGRESS,
				ChallengeStatus.FINALIZING, ChallengeStatus.ENDED)) {
			Challenge challenge = challenge(status, 1_000);
			returns(challenge);

			BusinessException error = assertThrows(BusinessException.class, () -> service.discard(HOST, CHALLENGE));

			assertEquals(ChallengeErrorCode.NOT_START_HELD, error.getErrorCode());
			assertEquals(status, challenge.getStatus());
		}
		verifyNoInteractions(participation, notice);
	}

	private void returns(Challenge challenge) {
		when(challenges.findById(CHALLENGE)).thenReturn(Optional.of(challenge));
		when(challenges.findByIdForUpdate(CHALLENGE)).thenReturn(Optional.of(challenge));
	}

	private static Challenge challenge(ChallengeStatus status, int pointAmount) {
		Challenge challenge = new Challenge(HOST, "아침 독서", ChallengeVisibility.PUBLIC, ChallengeCategory.STUDY, "소개", null,
				"미션", "규칙", null, "123456", java.time.LocalDate.of(2026, 12, 1), java.time.LocalDate.of(2026, 12, 14), 5,
				pointAmount, null);
		try {
			Field field = Challenge.class.getDeclaredField("status");
			field.setAccessible(true);
			field.set(challenge, status);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return challenge;
	}
}

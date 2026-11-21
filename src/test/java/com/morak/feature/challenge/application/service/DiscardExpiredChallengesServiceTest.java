package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.port.out.ChallengeDiscardNotice;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.DiscardedParticipant;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.service.DiscardExpiredChallengesService.Summary;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;

/** 자동 폐기 배치(위키 §4.9 ③)와 {@link DiscardChallengeTransaction#discardIfExpired}. 트랜잭션 관리자는 목이다. */
class DiscardExpiredChallengesServiceTest {

	private static final long HOST = 1L;
	private static final long CHALLENGE = 10L;
	private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");
	// 시작 예정 11/1, 마감 11/3 23:59 → 11/4 00:00 자동 폐기(위키 §7)
	private static final LocalDate START = LocalDate.of(2026, 11, 1);
	private static final LocalDate DUE_DAY = LocalDate.of(2026, 11, 4);

	private final ChallengeRepository challenges = mock(ChallengeRepository.class);
	private final ChallengeParticipation participation = mock(ChallengeParticipation.class);
	private final ChallengeParticipationReader reader = mock(ChallengeParticipationReader.class);
	private final ChallengeDiscardNotice notice = mock(ChallengeDiscardNotice.class);
	private final DiscardChallengeTransaction transaction = new DiscardChallengeTransaction(
			challenges, Optional.of(participation), reader, Clock.fixed(NOW, ZoneOffset.UTC));
	private final DiscardExpiredChallengesService service = service(100);

	@Test
	void discardsExpiredChallengeRefundsAndNotifiesEveryoneIncludingHost() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD, 1_000);
		candidates(List.of(CHALLENGE));
		returns(CHALLENGE, challenge);
		when(participation.lockPointAccountsForDiscard(CHALLENGE)).thenReturn(Set.of(1L, 2L, 3L));
		when(participation.discardAll(CHALLENGE, "아침 독서", Set.of(1L, 2L, 3L), NOW)).thenReturn(List.of(
				new DiscardedParticipant(100, 1L, true, 1_000),
				new DiscardedParticipant(101, 2L, false, 1_000),
				new DiscardedParticipant(102, 3L, false, 1_000)));

		Summary summary = service.runFor(DUE_DAY);

		assertEquals(new Summary(1, 0, 0), summary);
		assertEquals(ChallengeStatus.DISCARDED, challenge.getStatus());
		verify(notice).autoDiscarded(1L, CHALLENGE, 100, "아침 독서", 1_000);
		verify(notice).autoDiscarded(2L, CHALLENGE, 101, "아침 독서", 1_000);
		verify(notice).autoDiscarded(3L, CHALLENGE, 102, "아침 독서", 1_000);
		verify(notice, never()).discardedByHost(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	void locksPointAccountsBeforeChallengeAndChallengeBeforeParticipants() {
		candidates(List.of(CHALLENGE));
		returns(CHALLENGE, challenge(ChallengeStatus.START_HELD, 1_000));
		when(participation.lockPointAccountsForDiscard(CHALLENGE)).thenReturn(Set.of(1L));
		when(participation.discardAll(anyLong(), anyString(), any(), any()))
				.thenReturn(List.of(new DiscardedParticipant(100, 1L, true, 1_000)));

		service.runFor(DUE_DAY);

		InOrder order = inOrder(participation, challenges);
		order.verify(participation).lockPointAccountsForDiscard(CHALLENGE);
		order.verify(challenges).findByIdForUpdate(CHALLENGE);
		order.verify(participation).discardAll(CHALLENGE, "아침 독서", Set.of(1L), NOW);
	}

	@Test
	void challengeRescheduledBeforeLockIsSkippedWithoutError() {
		candidates(List.of(CHALLENGE));
		when(challenges.findById(CHALLENGE)).thenReturn(Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		// 잠그는 사이 방장이 기간을 재설정했다.
		when(challenges.findByIdForUpdate(CHALLENGE))
				.thenReturn(Optional.of(challenge(ChallengeStatus.RECRUITING, 1_000)));

		Summary summary = service.runFor(DUE_DAY);

		assertEquals(new Summary(0, 1, 0), summary);
		verify(participation, never()).discardAll(anyLong(), anyString(), any(), any());
		verifyNoInteractions(notice);
	}

	@Test
	void challengeDiscardedByHostBeforeLockIsNotRefundedAgain() {
		candidates(List.of(CHALLENGE));
		when(challenges.findById(CHALLENGE)).thenReturn(Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		when(challenges.findByIdForUpdate(CHALLENGE))
				.thenReturn(Optional.of(challenge(ChallengeStatus.DISCARDED, 1_000)));

		Summary summary = service.runFor(DUE_DAY);

		assertEquals(new Summary(0, 1, 0), summary);
		verify(participation, never()).discardAll(anyLong(), anyString(), any(), any());
		verifyNoInteractions(notice);
	}

	@Test
	void challengeBeforeDeadlineIsSkippedWithoutLocking() {
		candidates(List.of(CHALLENGE));
		returns(CHALLENGE, challenge(ChallengeStatus.START_HELD, 1_000));

		Summary summary = service.runFor(LocalDate.of(2026, 11, 3));

		assertEquals(new Summary(0, 1, 0), summary);
		verify(challenges, never()).findByIdForUpdate(anyLong());
		verifyNoInteractions(participation, notice);
	}

	@Test
	void challengeThatFilledBeforeDeadlineIsLeftForStartProcessing() {
		Challenge challenge = challenge(ChallengeStatus.START_HELD, 1_000);
		candidates(List.of(CHALLENGE));
		returns(CHALLENGE, challenge);
		when(reader.countJoinedBefore(CHALLENGE, challenge.recruitDeadlineEnd())).thenReturn(3);

		Summary summary = service.runFor(DUE_DAY);

		assertEquals(new Summary(0, 1, 0), summary);
		assertEquals(ChallengeStatus.START_HELD, challenge.getStatus());
		verify(participation, never()).discardAll(anyLong(), anyString(), any(), any());
		verifyNoInteractions(notice);
	}

	@Test
	void retriesWhenApplicationsChangedAndNotifiesOnce() {
		candidates(List.of(CHALLENGE));
		// 트랜잭션이 롤백된 것처럼 매번 새로 읽은 시작 보류 챌린지를 돌려준다.
		when(challenges.findById(CHALLENGE)).thenAnswer(i -> Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		when(challenges.findByIdForUpdate(CHALLENGE))
				.thenAnswer(i -> Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		when(participation.lockPointAccountsForDiscard(CHALLENGE)).thenReturn(Set.of(1L), Set.of(1L, 2L));
		when(participation.discardAll(anyLong(), anyString(), any(), any()))
				.thenThrow(new BusinessException(ChallengeErrorCode.DISCARD_APPLICATIONS_CHANGED))
				.thenReturn(List.of(new DiscardedParticipant(100, 1L, true, 1_000),
						new DiscardedParticipant(101, 2L, false, 1_000)));

		Summary summary = service.runFor(DUE_DAY);

		assertEquals(new Summary(1, 0, 0), summary);
		verify(participation).discardAll(CHALLENGE, "아침 독서", Set.of(1L, 2L), NOW);
		verify(notice, times(1)).autoDiscarded(1L, CHALLENGE, 100, "아침 독서", 1_000);
		verify(notice, times(1)).autoDiscarded(2L, CHALLENGE, 101, "아침 독서", 1_000);
	}

	@Test
	void givesUpAfterMaxAttemptsAndCountsFailure() {
		candidates(List.of(CHALLENGE));
		when(challenges.findById(CHALLENGE)).thenAnswer(i -> Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		when(challenges.findByIdForUpdate(CHALLENGE))
				.thenAnswer(i -> Optional.of(challenge(ChallengeStatus.START_HELD, 1_000)));
		when(participation.discardAll(anyLong(), anyString(), any(), any()))
				.thenThrow(new BusinessException(ChallengeErrorCode.DISCARD_APPLICATIONS_CHANGED));

		Summary summary = service.runFor(DUE_DAY);

		assertEquals(new Summary(0, 0, 1), summary);
		verify(participation, times(DiscardRetry.MAX_ATTEMPTS)).discardAll(anyLong(), anyString(), any(), any());
		verifyNoInteractions(notice);
	}

	@Test
	void oneFailureDoesNotStopOthers() {
		candidates(List.of(10L, 11L));
		returns(10L, challenge(ChallengeStatus.START_HELD, 0));
		Challenge other = challenge(ChallengeStatus.START_HELD, 0);
		returns(11L, other);
		when(participation.discardAll(eq(10L), anyString(), any(), any()))
				.thenReturn(List.of(new DiscardedParticipant(100, 1L, true, 0)));
		when(participation.discardAll(eq(11L), anyString(), any(), any()))
				.thenReturn(List.of(new DiscardedParticipant(200, 5L, true, 0)));
		doThrow(new IllegalStateException("알림 실패")).when(notice).autoDiscarded(1L, 10L, 100, "아침 독서", 0);

		Summary summary = service.runFor(DUE_DAY);

		assertEquals(new Summary(1, 0, 1), summary);
		verify(notice).autoDiscarded(5L, 11L, 200, "아침 독서", 0);
		assertEquals(ChallengeStatus.DISCARDED, other.getStatus());
	}

	@Test
	void missingNoticeAdapterFailsTheChallenge() {
		DiscardExpiredChallengesService withoutNotice = new DiscardExpiredChallengesService(challenges, transaction,
				Optional.empty(), mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC), 100);
		candidates(List.of(CHALLENGE));
		returns(CHALLENGE, challenge(ChallengeStatus.START_HELD, 0));
		when(participation.discardAll(anyLong(), anyString(), any(), any()))
				.thenReturn(List.of(new DiscardedParticipant(100, 1L, true, 0)));

		assertEquals(new Summary(0, 0, 1), withoutNotice.runFor(DUE_DAY));
	}

	@Test
	void readsNextPageAfterLastIdOfFullPage() {
		DiscardExpiredChallengesService paged = service(2);
		when(challenges.findDiscardDueIds(DUE_DAY, 0, 2)).thenReturn(List.of(10L, 11L));
		when(challenges.findDiscardDueIds(DUE_DAY, 11, 2)).thenReturn(List.of(12L));
		for (long id : List.of(10L, 11L, 12L)) {
			returns(id, challenge(ChallengeStatus.START_HELD, 0));
		}
		when(participation.discardAll(anyLong(), anyString(), any(), any())).thenReturn(List.of());

		Summary summary = paged.runFor(DUE_DAY);

		assertEquals(new Summary(3, 0, 0), summary);
		verify(challenges).findDiscardDueIds(DUE_DAY, 11, 2);
	}

	private DiscardExpiredChallengesService service(int pageSize) {
		return new DiscardExpiredChallengesService(challenges, transaction, Optional.of(notice),
				mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC), pageSize);
	}

	private void candidates(List<Long> ids) {
		when(challenges.findDiscardDueIds(any(), anyLong(), anyInt())).thenReturn(List.of());
		when(challenges.findDiscardDueIds(any(), eq(0L), anyInt())).thenReturn(ids);
	}

	private void returns(long id, Challenge challenge) {
		when(challenges.findById(id)).thenReturn(Optional.of(challenge));
		when(challenges.findByIdForUpdate(id)).thenReturn(Optional.of(challenge));
	}

	private static Challenge challenge(ChallengeStatus status, int pointAmount) {
		Challenge challenge = new Challenge(HOST, "아침 독서", ChallengeVisibility.PUBLIC, ChallengeCategory.STUDY, "소개", null,
				"미션", "규칙", null, "123456", START, START.plusDays(13), 5, pointAmount, null);
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

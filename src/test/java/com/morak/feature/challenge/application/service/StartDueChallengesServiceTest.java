package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.feature.challenge.application.service.StartChallengeTransaction.Outcome;
import com.morak.feature.challenge.application.service.StartDueChallengesService.Summary;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartDueChallengesServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

	private final ChallengeRepository challenges = mock(ChallengeRepository.class);
	private final StartChallengeTransaction transaction = mock(StartChallengeTransaction.class);
	/** 2026-10-31 15:00 UTC = 2026-11-01 00:00 KST */
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneOffset.UTC);
	private final StartDueChallengesService service = new StartDueChallengesService(challenges, transaction, clock, 2);

	@Test
	void runUsesKstDate() {
		when(challenges.findStartDueIds(TODAY, 0, 2)).thenReturn(List.of());

		assertEquals(new Summary(0, 0, 0, 0), service.run());

		verify(challenges).findStartDueIds(TODAY, 0, 2);
	}

	@Test
	void readsPagesByIdAndCountsOutcomes() {
		when(challenges.findStartDueIds(TODAY, 0, 2)).thenReturn(List.of(1L, 2L));
		when(challenges.findStartDueIds(TODAY, 2, 2)).thenReturn(List.of(5L, 7L));
		when(challenges.findStartDueIds(TODAY, 7, 2)).thenReturn(List.of(9L));
		when(transaction.process(1L, TODAY)).thenReturn(Outcome.STARTED);
		when(transaction.process(2L, TODAY)).thenReturn(Outcome.HELD);
		when(transaction.process(5L, TODAY)).thenReturn(Outcome.UNCHANGED);
		when(transaction.process(7L, TODAY)).thenReturn(Outcome.STARTED);
		when(transaction.process(9L, TODAY)).thenReturn(Outcome.HELD);

		assertEquals(new Summary(2, 2, 1, 0), service.runFor(TODAY));
	}

	@Test
	void oneFailureDoesNotStopOthers() {
		when(challenges.findStartDueIds(TODAY, 0, 2)).thenReturn(List.of(1L));
		when(transaction.process(1L, TODAY)).thenThrow(new IllegalStateException("db"));

		assertEquals(new Summary(0, 0, 0, 1), service.runFor(TODAY));

		StartDueChallengesService bigger = new StartDueChallengesService(challenges, transaction, clock, 3);
		when(challenges.findStartDueIds(TODAY, 0, 3)).thenReturn(List.of(1L, 2L));
		when(transaction.process(2L, TODAY)).thenReturn(Outcome.STARTED);

		assertEquals(new Summary(1, 0, 0, 1), bigger.runFor(TODAY));
		verify(challenges, never()).findStartDueIds(eq(TODAY), anyLong(), eq(4));
	}

	@Test
	void stopsWhenPageIsNotFull() {
		when(challenges.findStartDueIds(TODAY, 0, 2)).thenReturn(List.of(3L));
		when(transaction.process(3L, TODAY)).thenReturn(Outcome.UNCHANGED);

		service.runFor(TODAY);

		verify(challenges, never()).findStartDueIds(eq(TODAY), eq(3L), anyInt());
	}
}

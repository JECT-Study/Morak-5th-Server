package com.morak.feature.challenge.infrastructure.scheduling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.doThrow;

import com.morak.feature.challenge.application.port.out.ChallengeCertificationFailures;
import com.morak.feature.challenge.application.service.DiscardExpiredChallengesService;
import com.morak.feature.challenge.application.service.StartDueChallengesService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.scheduling.annotation.Scheduled;

class ChallengeDailySchedulerTest {

	// 2026-11-03T15:00:00Z = 2026-11-04 00:00 KST
	private static final Instant MIDNIGHT_KST = Instant.parse("2026-11-03T15:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

	private final ChallengeCertificationFailures failures = mock(ChallengeCertificationFailures.class);
	private final StartDueChallengesService startDue = mock(StartDueChallengesService.class);
	private final DiscardExpiredChallengesService discardExpired = mock(DiscardExpiredChallengesService.class);
	private final ChallengeDailyScheduler scheduler =
			new ChallengeDailyScheduler(failures, startDue, discardExpired, Clock.fixed(MIDNIGHT_KST, ZoneOffset.UTC));

	@Test
	void runsFailedCreationThenStartThenAutoDiscardWithSameKstDate() {
		scheduler.runDaily();

		InOrder order = inOrder(failures, startDue, discardExpired);
		order.verify(failures).createMissed(TODAY);
		order.verify(startDue).runFor(TODAY);
		order.verify(discardExpired).runFor(TODAY);
	}

	@Test
	void failedCreationFailureIsLoggedAndLaterStepsStillRun() {
		doThrow(new IllegalStateException("boom")).when(failures).createMissed(TODAY);

		assertDoesNotThrow(scheduler::runDaily);
		verify(startDue).runFor(TODAY);
		verify(discardExpired).runFor(TODAY);
	}

	@Test
	void startFailureIsLoggedAndAutoDiscardStillRuns() {
		when(startDue.runFor(TODAY)).thenThrow(new IllegalStateException("boom"));

		assertDoesNotThrow(scheduler::runDaily);
		verify(discardExpired).runFor(TODAY);
	}

	@Test
	void autoDiscardFailureIsLoggedNotThrown() {
		when(discardExpired.runFor(TODAY)).thenThrow(new IllegalStateException("boom"));

		assertDoesNotThrow(scheduler::runDaily);
	}

	@Test
	void runsDailyAtMidnightInSeoulWithConfigurableCron() throws NoSuchMethodException {
		Scheduled scheduled = ChallengeDailyScheduler.class.getMethod("runDaily").getAnnotation(Scheduled.class);

		assertEquals("Asia/Seoul", scheduled.zone());
		assertEquals("${morak.challenge.daily.cron:0 0 0 * * *}", scheduled.cron());
	}
}

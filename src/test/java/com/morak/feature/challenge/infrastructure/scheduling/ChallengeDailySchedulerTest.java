package com.morak.feature.challenge.infrastructure.scheduling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.feature.challenge.application.service.StartDueChallengesService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ChallengeDailySchedulerTest {

	private final StartDueChallengesService startDue = mock(StartDueChallengesService.class);
	private final ChallengeDailyScheduler scheduler = new ChallengeDailyScheduler(startDue);

	@Test
	void runsStartProcessing() {
		scheduler.runDaily();

		verify(startDue).run();
	}

	@Test
	void failureIsLoggedNotThrown() {
		when(startDue.run()).thenThrow(new IllegalStateException("boom"));

		assertDoesNotThrow(scheduler::runDaily);
	}

	@Test
	void runsDailyAtMidnightInSeoulWithConfigurableCron() throws NoSuchMethodException {
		Scheduled scheduled = ChallengeDailyScheduler.class.getMethod("runDaily").getAnnotation(Scheduled.class);

		assertEquals("Asia/Seoul", scheduled.zone());
		assertEquals("${morak.challenge.daily.cron:0 0 0 * * *}", scheduled.cron());
	}
}

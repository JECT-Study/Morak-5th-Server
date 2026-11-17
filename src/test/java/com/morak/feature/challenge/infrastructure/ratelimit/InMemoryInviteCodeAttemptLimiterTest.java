package com.morak.feature.challenge.infrastructure.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryInviteCodeAttemptLimiterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T03:00:00Z"));
	private final InMemoryInviteCodeAttemptLimiter limiter = new InMemoryInviteCodeAttemptLimiter(clock);

	@Test
	void blocksAfterTenFailuresWithinWindow() {
		fail(7L, 9);
		assertFalse(limiter.isBlocked(7L));

		fail(7L, 1);
		assertTrue(limiter.isBlocked(7L));
	}

	@Test
	void countsEachUserSeparately() {
		fail(7L, 10);

		assertTrue(limiter.isBlocked(7L));
		assertFalse(limiter.isBlocked(8L));
	}

	@Test
	void unblocksAfterBlockDurationAndStartsCountingAgain() {
		fail(7L, 10);
		clock.advance(Duration.ofMinutes(10).minusSeconds(1));
		assertTrue(limiter.isBlocked(7L));

		clock.advance(Duration.ofSeconds(1));
		assertFalse(limiter.isBlocked(7L));

		fail(7L, 9);
		assertFalse(limiter.isBlocked(7L));
	}

	@Test
	void failuresBlockingDoesNotExtendBlock() {
		fail(7L, 10);
		clock.advance(Duration.ofMinutes(5));
		fail(7L, 3);

		clock.advance(Duration.ofMinutes(5));
		assertFalse(limiter.isBlocked(7L));
	}

	@Test
	void resetsCountWhenWindowEndsBeforeLimit() {
		fail(7L, 9);
		clock.advance(Duration.ofMinutes(10));

		fail(7L, 1);
		assertFalse(limiter.isBlocked(7L));
	}

	private void fail(long userId, int times) {
		for (int i = 0; i < times; i++) {
			limiter.recordFailure(userId);
		}
	}

	private static class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}
}

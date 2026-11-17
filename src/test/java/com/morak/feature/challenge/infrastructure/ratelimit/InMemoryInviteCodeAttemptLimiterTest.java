package com.morak.feature.challenge.infrastructure.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InMemoryInviteCodeAttemptLimiterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T03:00:00Z"));
	private final InMemoryInviteCodeAttemptLimiter limiter = new InMemoryInviteCodeAttemptLimiter(clock);

	@Test
	void blocksAfterTenFailuresWithinWindow() {
		fail(7L, 9);
		assertTrue(limiter.tryAcquire(7L).allowed());
		limiter.release(7L, true);

		var decision = limiter.tryAcquire(7L);
		assertFalse(decision.allowed());
		assertEquals(Duration.ofMinutes(10), decision.retryAfter());
	}

	@Test
	void countsEachUserSeparately() {
		fail(7L, 10);

		assertFalse(limiter.tryAcquire(7L).allowed());
		assertTrue(limiter.tryAcquire(8L).allowed());
	}

	@Test
	void successfulLookupsDoNotCountButDoNotResetFailures() {
		fail(7L, 9);
		for (int i = 0; i < 20; i++) {
			assertTrue(limiter.tryAcquire(7L).allowed());
			limiter.release(7L, false);
		}

		fail(7L, 1);
		assertFalse(limiter.tryAcquire(7L).allowed());
	}

	@Test
	void inFlightLookupsCountTowardLimit() {
		fail(7L, 7);
		for (int i = 0; i < 3; i++) {
			assertTrue(limiter.tryAcquire(7L).allowed());
		}

		var decision = limiter.tryAcquire(7L);
		assertFalse(decision.allowed());
		assertEquals(InMemoryInviteCodeAttemptLimiter.IN_FLIGHT_RETRY, decision.retryAfter());

		limiter.release(7L, false);
		assertTrue(limiter.tryAcquire(7L).allowed());
	}

	@Test
	void concurrentRequestsCannotExceedLimit() throws Exception {
		int threads = 50;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		var results = new ArrayList<Future<Boolean>>();
		try {
			for (int i = 0; i < threads; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return limiter.tryAcquire(7L).allowed();
				}));
			}
			start.countDown();
			int allowed = 0;
			for (Future<Boolean> result : results) {
				allowed += result.get() ? 1 : 0;
			}
			assertEquals(InMemoryInviteCodeAttemptLimiter.MAX_FAILURES, allowed);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void unblocksAfterBlockDurationAndStartsCountingAgain() {
		fail(7L, 10);
		clock.advance(Duration.ofMinutes(10).minusSeconds(1));
		var decision = limiter.tryAcquire(7L);
		assertFalse(decision.allowed());
		assertEquals(Duration.ofSeconds(1), decision.retryAfter());

		clock.advance(Duration.ofSeconds(1));
		fail(7L, 9);
		assertTrue(limiter.tryAcquire(7L).allowed());
	}

	@Test
	void failureOfLookupStartedBeforeBlockDoesNotExtendBlock() {
		fail(7L, 9);
		assertTrue(limiter.tryAcquire(7L).allowed());
		limiter.release(7L, true);
		clock.advance(Duration.ofMinutes(5));
		limiter.release(7L, true);

		clock.advance(Duration.ofMinutes(5));
		assertTrue(limiter.tryAcquire(7L).allowed());
	}

	@Test
	void resetsCountWhenWindowEndsBeforeLimit() {
		fail(7L, 9);
		clock.advance(Duration.ofMinutes(10));

		fail(7L, 9);
		assertTrue(limiter.tryAcquire(7L).allowed());
	}

	private void fail(long userId, int times) {
		for (int i = 0; i < times; i++) {
			assertTrue(limiter.tryAcquire(userId).allowed());
			limiter.release(userId, true);
		}
	}

	private static class MutableClock extends Clock {

		private volatile Instant now;

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

package com.morak.feature.ad.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.dto.result.AdRewardResult;
import com.morak.feature.ad.application.dto.result.AdRewardSession;
import com.morak.feature.ad.application.port.out.AdRewardPoints;
import com.morak.feature.ad.application.port.out.AdTokenCodec;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.morak.feature.ad.domain.vo.AdToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdRewardServiceTest {

	private static final long USER = 10L;
	private static final long OTHER_USER = 20L;
	private static final Duration MIN_WATCH = Duration.ofSeconds(5);
	private static final Duration VALIDITY = Duration.ofMinutes(10);

	private final FakeClock clock = new FakeClock(Instant.parse("2026-11-18T03:00:00Z"));
	private final FakePoints points = new FakePoints();
	private final FakeCodec codec = new FakeCodec();
	private final StartAdRewardService start = new StartAdRewardService(points, codec, clock, MIN_WATCH, VALIDITY);
	private final ClaimAdRewardService claim = new ClaimAdRewardService(points, codec, clock, true, MIN_WATCH);

	@Test
	void startIssuesTokenWithoutConsumingCount() {
		AdRewardSession first = start.start(USER);
		AdRewardSession second = start.start(USER);

		assertEquals(5, first.remainingToday());
		assertEquals(5, second.remainingToday());
		assertEquals(5, first.minWatchSeconds());
		assertEquals(clock.instant().plus(VALIDITY), first.expiresAt());
	}

	@Test
	void claimAfterMinWatchGrants500() {
		AdRewardSession session = start.start(USER);
		clock.advance(MIN_WATCH);

		AdRewardResult result = claim.claim(USER, session.token());

		assertEquals(500, result.amount());
		assertEquals(500, result.balance());
		assertEquals(4, result.remainingToday());
	}

	@Test
	void abandonedWatchDoesNotConsumeCount() {
		start.start(USER);
		start.start(USER);

		assertEquals(0, points.todayRewardCount(USER));
		assertEquals(5, start.start(USER).remainingToday());
	}

	@Test
	void claimBeforeMinWatchIsRejected() {
		AdRewardSession session = start.start(USER);
		clock.advance(MIN_WATCH.minusMillis(1));

		assertError(AdErrorCode.WATCH_TIME_NOT_ENOUGH, () -> claim.claim(USER, session.token()));
		assertEquals(0, points.todayRewardCount(USER));
	}

	@Test
	void expiredTokenIsRejected() {
		AdRewardSession session = start.start(USER);
		clock.advance(VALIDITY);

		assertError(AdErrorCode.EXPIRED_TOKEN, () -> claim.claim(USER, session.token()));
	}

	@Test
	void otherUsersTokenIsRejected() {
		AdRewardSession session = start.start(USER);
		clock.advance(MIN_WATCH);

		assertError(AdErrorCode.INVALID_TOKEN, () -> claim.claim(OTHER_USER, session.token()));
		assertEquals(0, points.todayRewardCount(OTHER_USER));
		assertEquals(0, points.todayRewardCount(USER));
	}

	@Test
	void unknownTokenIsRejected() {
		assertError(AdErrorCode.INVALID_TOKEN, () -> claim.claim(USER, "forged"));
	}

	@Test
	void sameTokenIsGrantedOnce() {
		AdRewardSession session = start.start(USER);
		clock.advance(MIN_WATCH);

		AdRewardResult first = claim.claim(USER, session.token());
		AdRewardResult second = claim.claim(USER, session.token());

		assertEquals(first.balance(), second.balance());
		assertEquals(1, points.todayRewardCount(USER));
		assertEquals(500, points.balanceOf(USER));
	}

	@Test
	void sixthRewardIsRejectedAndStartIsRejectedToo() {
		for (int i = 0; i < 5; i++) {
			AdRewardSession session = start.start(USER);
			clock.advance(MIN_WATCH);
			claim.claim(USER, session.token());
		}
		assertEquals(2_500, points.balanceOf(USER));

		assertError(AdErrorCode.DAILY_LIMIT_EXCEEDED, () -> start.start(USER));
	}

	@Test
	void sixthClaimWithAlreadyIssuedTokenIsRejected() {
		AdRewardSession pending = start.start(USER);
		for (int i = 0; i < 5; i++) {
			AdRewardSession session = start.start(USER);
			clock.advance(MIN_WATCH);
			claim.claim(USER, session.token());
		}

		assertError(AdErrorCode.DAILY_LIMIT_EXCEEDED, () -> claim.claim(USER, pending.token()));
		assertEquals(2_500, points.balanceOf(USER));
	}

	@Test
	void retryOfFifthRewardStillSucceeds() {
		AdRewardSession last = null;
		for (int i = 0; i < 5; i++) {
			last = start.start(USER);
			clock.advance(MIN_WATCH);
			claim.claim(USER, last.token());
		}

		AdRewardResult retry = claim.claim(USER, last.token());

		assertEquals(500, retry.amount());
		assertEquals(2_500, points.balanceOf(USER));
	}

	@Test
	void claimIsClosedWhenMockIsDisabled() {
		ClaimAdRewardService closed = new ClaimAdRewardService(points, codec, clock, false, MIN_WATCH);
		AdRewardSession session = start.start(USER);
		clock.advance(MIN_WATCH);

		assertError(AdErrorCode.UNAVAILABLE, () -> closed.claim(USER, session.token()));
		assertEquals(0, points.todayRewardCount(USER));
	}

	private static void assertError(AdErrorCode expected, org.junit.jupiter.api.function.Executable executable) {
		BusinessException exception = assertThrows(BusinessException.class, executable);
		assertEquals(expected, exception.getErrorCode());
	}

	private static class FakeClock extends Clock {

		private Instant now;

		FakeClock(Instant now) { this.now = now; }

		void advance(Duration duration) { now = now.plus(duration); }

		@Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(java.time.ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}

	/** 토큰 내용을 메모리에 두고 문자열로 가리키는 가짜 코덱. 서명 검증은 코덱 테스트에서 한다. */
	private static class FakeCodec implements AdTokenCodec {

		private final Map<String, AdToken> tokens = new HashMap<>();

		@Override
		public String issue(AdToken token) {
			String raw = "token-" + token.id();
			tokens.put(raw, token);
			return raw;
		}

		@Override
		public AdToken parse(String raw) {
			AdToken token = tokens.get(raw);
			if (token == null) {
				throw new BusinessException(AdErrorCode.INVALID_TOKEN, "unknown");
			}
			return token;
		}
	}

	/** 포인트 도메인의 멱등키·하루 한도 규칙을 흉내 낸다. 실제 잠금과 동시성은 통합 테스트에서 확인한다. */
	private static class FakePoints implements AdRewardPoints {

		private final Map<Long, Integer> counts = new HashMap<>();
		private final Map<Long, Long> balances = new HashMap<>();
		private final Set<String> keys = new HashSet<>();
		private final Map<String, Long> firstResults = new HashMap<>();

		@Override
		public int todayRewardCount(long userId) {
			return counts.getOrDefault(userId, 0);
		}

		@Override
		public long grantReward(long userId, String idempotencyKey, long amount, int dailyLimit) {
			if (keys.contains(idempotencyKey)) {
				return firstResults.get(idempotencyKey);
			}
			if (todayRewardCount(userId) >= dailyLimit) {
				throw new BusinessException(AdErrorCode.DAILY_LIMIT_EXCEEDED, "limit");
			}
			keys.add(idempotencyKey);
			counts.merge(userId, 1, Integer::sum);
			long balance = balances.merge(userId, amount, Long::sum);
			firstResults.put(idempotencyKey, balance);
			return balance;
		}

		long balanceOf(long userId) { return balances.getOrDefault(userId, 0L); }
	}
}

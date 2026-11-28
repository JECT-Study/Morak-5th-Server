package com.morak.feature.ad.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.dto.result.AdRewardResult;
import com.morak.feature.ad.application.dto.result.AdSsvCallback;
import com.morak.feature.ad.application.port.out.AdRewardPoints;
import com.morak.feature.ad.application.port.out.AdSsvVerifier;
import com.morak.feature.ad.application.port.out.AdTokenCodec;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.morak.feature.ad.domain.vo.AdToken;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClaimAdRewardBySsvServiceTest {

	private static final long USER = 10L;
	private static final Instant ISSUED = Instant.parse("2026-11-28T03:00:00Z");
	private static final Instant EXPIRES = ISSUED.plusSeconds(600);
	private static final String AD_UNIT = "1234567890";

	private final FakePoints points = new FakePoints();
	private final FakeCodec codec = new FakeCodec();
	private final FakeVerifier verifier = new FakeVerifier();
	private final ClaimAdRewardBySsvService service =
			new ClaimAdRewardBySsvService(verifier, codec, points, true, List.of("ca-app-pub-1111~2222/" + AD_UNIT));

	@Test
	void validCallbackGrants500() {
		String token = codec.issue(new AdToken("t1", USER, ISSUED, EXPIRES));
		verifier.next = callback(AD_UNIT, "10", token, ISSUED.plusSeconds(30));

		AdRewardResult result = service.claim("query");

		assertEquals(500, result.amount());
		assertEquals(500, result.balance());
		assertEquals(4, result.remainingToday());
	}

	@Test
	void sameCallbackRepeatedIsGrantedOnce() {
		String token = codec.issue(new AdToken("t1", USER, ISSUED, EXPIRES));
		verifier.next = callback(AD_UNIT, "10", token, ISSUED.plusSeconds(30));

		service.claim("query");
		AdRewardResult retry = service.claim("query");

		assertEquals(500, retry.balance());
		assertEquals(1, points.todayRewardCount(USER));
	}

	@Test
	void adUnitMayBeConfiguredWithOrWithoutPublisherPrefix() {
		ClaimAdRewardBySsvService numericOnly =
				new ClaimAdRewardBySsvService(verifier, codec, points, true, List.of(AD_UNIT));
		String token = codec.issue(new AdToken("t1", USER, ISSUED, EXPIRES));
		verifier.next = callback(AD_UNIT, "10", token, ISSUED.plusSeconds(30));

		assertEquals(500, numericOnly.claim("query").amount());
	}

	@Test
	void adUnitOfAnotherAccountIsRejected() {
		String token = codec.issue(new AdToken("t1", USER, ISSUED, EXPIRES));
		verifier.next = callback("999", "10", token, ISSUED.plusSeconds(30));

		assertError(AdErrorCode.INVALID_SSV, () -> service.claim("query"));
		assertEquals(0, points.todayRewardCount(USER));
	}

	@Test
	void tokenOfAnotherUserIsRejected() {
		String token = codec.issue(new AdToken("t1", 20L, ISSUED, EXPIRES));
		verifier.next = callback(AD_UNIT, "10", token, ISSUED.plusSeconds(30));

		assertError(AdErrorCode.INVALID_TOKEN, () -> service.claim("query"));
		assertEquals(0, points.todayRewardCount(USER));
		assertEquals(0, points.todayRewardCount(20L));
	}

	@Test
	void forgedTokenIsRejected() {
		verifier.next = callback(AD_UNIT, "10", "forged", ISSUED.plusSeconds(30));

		assertError(AdErrorCode.INVALID_TOKEN, () -> service.claim("query"));
	}

	@Test
	void rewardAtOrAfterExpiryIsRejected() {
		String token = codec.issue(new AdToken("t1", USER, ISSUED, EXPIRES));
		verifier.next = callback(AD_UNIT, "10", token, EXPIRES);

		assertError(AdErrorCode.EXPIRED_TOKEN, () -> service.claim("query"));
		assertEquals(0, points.todayRewardCount(USER));
	}

	@Test
	void lateDeliveryOfAnInTimeRewardStillGrants() {
		// 보상은 만료 전에 확정됐으니, 알림이 늦게 도착하는 것(서버 시계)은 상관없다.
		String token = codec.issue(new AdToken("t1", USER, ISSUED, EXPIRES));
		verifier.next = callback(AD_UNIT, "10", token, EXPIRES.minusSeconds(1));

		assertEquals(500, service.claim("query").amount());
	}

	@Test
	void sixthRewardOfTheDayIsRejected() {
		for (int i = 0; i < 5; i++) {
			String token = codec.issue(new AdToken("t" + i, USER, ISSUED, EXPIRES));
			verifier.next = callback(AD_UNIT, "10", token, ISSUED.plusSeconds(30));
			service.claim("query");
		}
		String sixth = codec.issue(new AdToken("t5", USER, ISSUED, EXPIRES));
		verifier.next = callback(AD_UNIT, "10", sixth, ISSUED.plusSeconds(30));

		assertError(AdErrorCode.DAILY_LIMIT_EXCEEDED, () -> service.claim("query"));
		assertEquals(2_500, points.balanceOf(USER));
	}

	@Test
	void invalidSignatureFromVerifierIsPropagatedAndGrantsNothing() {
		verifier.failure = new BusinessException(AdErrorCode.INVALID_SSV, "서명 불일치");

		assertError(AdErrorCode.INVALID_SSV, () -> service.claim("query"));
		assertEquals(0, points.todayRewardCount(USER));
	}

	@Test
	void closedWhenDisabledOrWithoutAdUnits() {
		ClaimAdRewardBySsvService disabled =
				new ClaimAdRewardBySsvService(verifier, codec, points, false, List.of(AD_UNIT));
		ClaimAdRewardBySsvService noAdUnits = new ClaimAdRewardBySsvService(verifier, codec, points, true, List.of());
		ClaimAdRewardBySsvService blankAdUnits =
				new ClaimAdRewardBySsvService(verifier, codec, points, true, List.of(" ", ""));
		String token = codec.issue(new AdToken("t1", USER, ISSUED, EXPIRES));
		verifier.next = callback(AD_UNIT, "10", token, ISSUED.plusSeconds(30));

		assertError(AdErrorCode.UNAVAILABLE, () -> disabled.claim("query"));
		assertError(AdErrorCode.UNAVAILABLE, () -> noAdUnits.claim("query"));
		assertError(AdErrorCode.UNAVAILABLE, () -> blankAdUnits.claim("query"));
		assertEquals(0, points.todayRewardCount(USER));
	}

	private static AdSsvCallback callback(String adUnit, String userId, String customData, Instant rewardedAt) {
		return new AdSsvCallback(adUnit, userId, customData, "TX-1", rewardedAt);
	}

	private static void assertError(AdErrorCode expected, org.junit.jupiter.api.function.Executable executable) {
		BusinessException exception = assertThrows(BusinessException.class, executable);
		assertEquals(expected, exception.getErrorCode());
	}

	/** 서명 확인은 GoogleAdSsvVerifierTest에서 한다. 여기서는 확인을 마친 결과만 돌려준다. */
	private static class FakeVerifier implements AdSsvVerifier {

		AdSsvCallback next;
		BusinessException failure;

		@Override
		public AdSsvCallback verify(String rawQuery) {
			if (failure != null) {
				throw failure;
			}
			return next;
		}
	}

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

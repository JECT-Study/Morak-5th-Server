package com.morak.feature.ad.domain.policy;

/** 광고 시청 보상 정책(위키 §4.6 [확정]): 완료 1회 500P, 하루 최대 5회. */
public final class AdRewardPolicy {

	public static final long REWARD_POINT = 500;
	public static final int DAILY_LIMIT = 5;

	private static final String KEY_PREFIX = "AD:";

	private AdRewardPolicy() {
	}

	/** 포인트 원장 멱등키. 같은 시청 토큰은 한 번만 지급된다. */
	public static String idempotencyKey(String tokenId) {
		return KEY_PREFIX + tokenId;
	}
}

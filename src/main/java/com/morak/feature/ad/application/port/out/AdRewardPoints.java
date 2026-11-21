package com.morak.feature.ad.application.port.out;

public interface AdRewardPoints {

	/** 오늘(KST) 받은 광고 보상 횟수. */
	int todayRewardCount(long userId);

	/**
	 * 광고 보상을 지급하고 지급 후 잔액을 돌려준다. 같은 idempotencyKey는 한 번만 지급되고 처음 결과를 돌려준다.
	 * 하루 한도에 이미 도달했으면 AD_DAILY_LIMIT_EXCEEDED.
	 */
	long grantReward(long userId, String idempotencyKey, long amount, int dailyLimit);
}

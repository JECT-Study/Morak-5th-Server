package com.morak.feature.ad.infrastructure.point;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.port.out.AdRewardPoints;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.morak.feature.point.application.service.PointService;
import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.point.domain.exception.PointErrorCode;
import org.springframework.stereotype.Component;

@Component
class PointAdRewardPoints implements AdRewardPoints {

	private final PointService points;

	PointAdRewardPoints(PointService points) {
		this.points = points;
	}

	@Override
	public int todayRewardCount(long userId) {
		return points.countTodayGrants(userId, PointGrantReason.AD_REWARD);
	}

	@Override
	public long grantReward(long userId, String idempotencyKey, long amount, int dailyLimit) {
		try {
			return points.grantWithDailyLimit(userId, PointGrantReason.AD_REWARD, amount, idempotencyKey, dailyLimit)
					.balanceAfter();
		} catch (BusinessException exception) {
			if (exception.getErrorCode() == PointErrorCode.DAILY_GRANT_LIMIT_EXCEEDED) {
				throw new BusinessException(AdErrorCode.DAILY_LIMIT_EXCEEDED, exception.getDetail(), exception);
			}
			throw exception;
		}
	}
}

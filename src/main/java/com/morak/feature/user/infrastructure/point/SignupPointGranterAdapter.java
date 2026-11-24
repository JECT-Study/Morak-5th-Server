package com.morak.feature.user.infrastructure.point;

import com.morak.feature.point.application.service.PointService;
import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.user.application.port.out.SignupPointGranter;
import org.springframework.stereotype.Component;

@Component
class SignupPointGranterAdapter implements SignupPointGranter {

	static final long SIGNUP_GIFT_AMOUNT = 10_000L;

	private final PointService pointService;

	SignupPointGranterAdapter(PointService pointService) {
		this.pointService = pointService;
	}

	@Override
	public void grant(long userId) {
		pointService.grant(userId, PointGrantReason.SIGNUP_GIFT, SIGNUP_GIFT_AMOUNT, "SIGNUP:" + userId);
	}
}

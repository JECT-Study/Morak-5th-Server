package com.morak.feature.participation.infrastructure.point;

import com.morak.feature.participation.application.port.out.ApplicantPoints;
import com.morak.feature.point.application.service.PointService;
import org.springframework.stereotype.Component;

@Component
class PointApplicantPoints implements ApplicantPoints {

	private final PointService points;

	PointApplicantPoints(PointService points) {
		this.points = points;
	}

	@Override
	public void lockAccount(long userId) {
		points.lockAccountForChallengeCreation(userId);
	}

	@Override
	public void hold(long userId, long participationId, String challengeTitle, int amount) {
		points.deposit(userId, participationId, challengeTitle, amount);
	}

	@Override
	public void release(long userId, long participationId, String challengeTitle, int amount) {
		points.refund(userId, participationId, challengeTitle, amount);
	}
}

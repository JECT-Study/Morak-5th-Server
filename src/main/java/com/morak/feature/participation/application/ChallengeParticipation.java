package com.morak.feature.participation.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChallengeParticipation {

	long countOngoing(long userId);

	void registerHost(long challengeId, long userId, Instant now);

	/** 포인트 처리가 필요한 대기 신청이 하나라도 있으면 변경 없이 empty를 반환한다. */
	Optional<List<InvalidatedApplication>> invalidatePendingWithoutPoints(long userId);

	record InvalidatedApplication(long id, long challengeId, long hostUserId) {}
}

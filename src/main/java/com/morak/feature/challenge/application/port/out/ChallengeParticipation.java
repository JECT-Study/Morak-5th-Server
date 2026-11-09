package com.morak.feature.challenge.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 참가 도메인에서 개설 트랜잭션에 연결할 기능. */
public interface ChallengeParticipation {

	/** 방장과 승인된 참가를 모두 포함하고, 승인 대기와 종료된 방은 제외한다. */
	long countOngoing(long userId);

	/** 개설자를 HOST·ACTIVE로 등록한다. 독립 트랜잭션으로 커밋하지 않는다. */
	void registerHost(long challengeId, long userId, Instant now);

	/** 포인트 처리가 필요한 대기 신청이 하나라도 있으면 변경 없이 empty를 반환한다. */
	Optional<List<InvalidatedApplication>> invalidatePendingWithoutPoints(long userId);

	record InvalidatedApplication(long id, long challengeId, long hostUserId) {}
}

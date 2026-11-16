package com.morak.feature.challenge.application.port.out;

import java.util.Optional;

public interface ChallengeParticipationReader {

	/** 개설한 챌린지와 승인받아 참여 중인 챌린지를 합쳐 센다. */
	int countOngoing(long userId);

	/** 신청 이력이 있으면 현재 참가 상태를 반환한다. */
	Optional<String> findStatus(long challengeId, long userId);
}

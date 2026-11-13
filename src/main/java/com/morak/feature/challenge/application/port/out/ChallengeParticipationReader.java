package com.morak.feature.challenge.application.port.out;

public interface ChallengeParticipationReader {

	/** 개설한 챌린지와 승인받아 참여 중인 챌린지를 합쳐 센다. */
	int countOngoing(long userId);
}

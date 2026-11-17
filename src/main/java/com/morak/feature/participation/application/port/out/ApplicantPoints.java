package com.morak.feature.participation.application.port.out;

/** 지원자의 포인트를 거는 일과 돌려주는 일. 모두 호출한 쪽의 트랜잭션에 참여한다. */
public interface ApplicantPoints {

	/** 락 순서(포인트 계정 → 챌린지 → 참가자)를 지키기 위해 챌린지를 잠그기 전에 계정을 잠근다. */
	void lockAccount(long userId);

	/** 지원 시 참여 포인트를 건다. 잔액이 부족하면 예외. 참가자당 한 번만 반영된다. */
	void hold(long userId, long participationId, String challengeTitle, int amount);

	/** 거절 시 걸어 둔 포인트를 전액 돌려준다. 참가자당 한 번만 반영된다. */
	void release(long userId, long participationId, String challengeTitle, int amount);
}

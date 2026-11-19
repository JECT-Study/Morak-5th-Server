package com.morak.feature.challenge.application.port.out;

/** 시작 전 폐기를 참가자에게 인앱 알림으로 남긴다. 호출한 쪽의 트랜잭션에 참여한다. */
public interface ChallengeDiscardNotice {

	/**
	 * 알림 #6 방장이 시작 전 폐기.
	 *
	 * @param refundedPoint 돌려준 포인트. 0이면 포인트 문장이 붙지 않는다
	 */
	void discardedByHost(long recipientUserId, long challengeId, long participationId, String challengeTitle, int refundedPoint);
}

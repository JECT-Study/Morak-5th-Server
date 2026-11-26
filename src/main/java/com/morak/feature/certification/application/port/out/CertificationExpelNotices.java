package com.morak.feature.certification.application.port.out;

/** 퇴장·조기 종료를 알린다. 호출한 트랜잭션에 함께 묶인다. */
public interface CertificationExpelNotices {

	/** 알림 #10 개인 실패 누적으로 퇴장 → 퇴장자. 참가마다 한 번만 저장한다. */
	void expelled(long recipientUserId, long challengeId, long participationId, String challengeTitle, int failCount,
			int pointAmount);

	/** 알림 #11 공개 챌린지 조기 종료 → 남은 참여자. 챌린지·사용자마다 한 번만 저장한다. */
	void earlyEnded(long recipientUserId, long challengeId, String challengeTitle, int pointAmount);
}

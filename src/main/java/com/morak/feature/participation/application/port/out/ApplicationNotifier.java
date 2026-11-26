package com.morak.feature.participation.application.port.out;

/** 신청과 그 처리 결과를 인앱 알림으로 남긴다. 호출한 쪽의 트랜잭션에 참여한다. */
public interface ApplicationNotifier {

	/** 알림 #13 참여 신청 접수. 신청을 받은 방장에게 간다. TODO(D-38) */
	void requested(long hostUserId, long challengeId, long participationId, String challengeTitle);

	/** 알림 #1 참가 신청 승인. */
	void approved(long applicantUserId, long challengeId, long participationId, String challengeTitle);

	/** 알림 #2 참가 신청 거절. pointAmount는 돌려준 포인트이고 0이면 포인트 문장이 붙지 않는다. */
	void rejected(long applicantUserId, long challengeId, long participationId, String challengeTitle, int pointAmount);

	/** 알림 #8 첫 부방장 지정. 개설 후 처음 합류한 참여자에게 간다. */
	void deputyAssigned(long deputyUserId, long challengeId, long participationId, String challengeTitle);
}

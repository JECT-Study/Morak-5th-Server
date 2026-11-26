package com.morak.feature.challenge.application.port.out;

import java.time.LocalDate;

/** 시작 전 기간 재설정을 참가자에게 인앱 알림으로 남긴다. 호출한 쪽의 트랜잭션에 참여한다. */
public interface ChallengeScheduleResetNotice {

	/** 알림 #5 방장이 기간 재설정. */
	void scheduleReset(long recipientUserId, long challengeId, long participationId, String challengeTitle,
			LocalDate startDate, LocalDate endDate);
}

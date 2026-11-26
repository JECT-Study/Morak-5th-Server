package com.morak.feature.challenge.application.port.out;

import java.time.LocalDate;

/** 시작 보류를 방장에게 인앱 알림으로 남긴다. 호출한 쪽의 트랜잭션에 참여한다. */
public interface ChallengeStartHeldNotice {

	/**
	 * 알림 #4 예정 시작일 최소 인원 미달(시작 보류).
	 *
	 * @param deadlineDate 선택 기한 날짜(그 날 23:59까지)
	 * @param startDate 보류된 예정 시작일. 재설정 후 다시 보류되면 새 알림이 가도록 중복 방지 키에 쓴다
	 */
	void startHeld(long recipientUserId, long challengeId, String challengeTitle, LocalDate deadlineDate, LocalDate startDate);
}

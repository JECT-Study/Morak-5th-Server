package com.morak.feature.certification.application.port.out;

import java.time.LocalDate;

/** 판정 결과를 알림·팀 채팅으로 알린다. 호출한 트랜잭션에 함께 묶인다. */
public interface CertificationJudgeNotices {

	/** 알림 #12 인증 거절 → 제출자. */
	void rejected(long recipientUserId, long challengeId, long certificationId, String challengeTitle, LocalDate certDate,
			int remainingFailCount);
}

package com.morak.feature.certification.application.port.out;

import com.morak.feature.certification.domain.vo.CertificationJudge;
import java.time.LocalDate;

/** 판정 결과를 알림·팀 채팅으로 알린다. 호출한 트랜잭션에 함께 묶인다. */
public interface CertificationJudgeNotices {

	/** 알림 #12 인증 거절 → 제출자. */
	void rejected(long recipientUserId, long challengeId, long certificationId, String challengeTitle, LocalDate certDate,
			int remainingFailCount);

	/**
	 * 알림 #9 판정 기한 초과 경고 → 판정 담당자. 같은 (챌린지, 담당 역할, 인증 날짜)는 한 번만 저장한다.
	 *
	 * @return 새로 저장했으면 true. 이미 보낸 경고면 false
	 */
	boolean judgeOverdue(long recipientUserId, long challengeId, String challengeTitle, CertificationJudge judge,
			LocalDate certDate, int warningCount);

	/** 경고 누적을 팀 채팅 SYSTEM 메시지로 공개한다. */
	void judgeOverdueToTeam(long challengeId, CertificationJudge judge, int warningCount);
}

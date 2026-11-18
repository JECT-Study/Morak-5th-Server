package com.morak.feature.certification.application.query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** 다른 모듈(채팅 등)에 공개하는 인증 조회 API. */
public interface CertificationQuery {

	/** certificationId -> 인증. 지정한 챌린지의 인증만 반환하고, 없거나 다른 챌린지의 인증은 결과에 없다. */
	Map<Long, CertificationInfo> findInChallenge(Long challengeId, Collection<Long> certificationIds);

	/**
	 * userId -> 모든 참여 챌린지를 합산한 누적 인증 횟수(개인 인증률의 분자·분모, 위키 §4.1). 판정이 끝난 인증만 센다.
	 * 판정 대기(PENDING)는 분자·분모에서 모두 뺀다. 인증 기록이 없는 사용자는 결과에 없다.
	 *
	 * <p>TODO(D-12): 면제일을 구분하는 컬럼이 아직 없어 면제일 제외는 반영하지 못한다.
	 */
	Map<Long, CertificationStats> findStatsByUserIds(Collection<Long> userIds);

	record CertificationInfo(Long certificationId, Long participationId, LocalDate certDate) {}

	/** @param approvedCount 승인(APPROVED, AUTO_APPROVED)된 횟수 @param obligationCount 판정이 끝난 인증 의무 횟수 */
	record CertificationStats(int approvedCount, int obligationCount) {}
}

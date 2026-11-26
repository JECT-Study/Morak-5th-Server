package com.morak.feature.certification.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 홈 카드 인증 집계 조회. */
public interface CertificationCardStore {

	/** 참가별 집계. 없는 참가는 결과에 없다. @param now 판정 마감(judge_due_at) 비교 시각 */
	List<CardRow> findCards(Collection<Long> participationIds, LocalDate today, Instant now);

	/** 챌린지들의 오늘 승인된 인증(ACTIVE 팀원만). 챌린지·제출 시각 순. */
	List<PhotoRow> findApprovedPhotos(Collection<Long> challengeIds, LocalDate today);

	/**
	 * @param teamTarget 어제까지 팀 인증 의무 횟수 합(퇴장일·조기 종료일부터 제외). 시작 전이면 null
	 * @param teamFailed 그 의무 중 실패(FAILED·REJECTED) 횟수
	 * @param todayOpen 오늘 인증 의무가 있는 날인지(진행 중이고 기간 안)
	 * @param todayTarget 오늘 인증해야 하는 팀원(ACTIVE) 수
	 * @param todayCertified 그중 오늘 제출한 수
	 */
	record CardRow(long participationId, long challengeId, Long teamTarget, long teamFailed, boolean todayOpen,
			int todayTarget, int todayCertified, String myTodayStatus, int pendingJudgeCount) {}

	record PhotoRow(long challengeId, long userId, String imageKey, Instant submittedAt) {}
}

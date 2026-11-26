package com.morak.feature.certification.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 다른 모듈(챌린지 홈 카드)에 공개하는 참가별 인증 집계. 위키 §4.5 팀 달성률, D-23 ⑤~⑦. */
public interface CertificationCardQuery {

	/**
	 * participationId -> 인증 집계. 카드 여러 장을 묶어서 조회한다. 없는 참가는 결과에 없다.
	 *
	 * @param today 집계 기준일(KST)
	 */
	Map<Long, CardStats> findCardStats(Collection<Long> participationIds, LocalDate today);

	/**
	 * @param teamAchievementRate (대상 − 실패) ÷ 대상 × 100, 소수 첫째 자리 half-up. 대상(어제까지의 의무)이 없으면 null
	 * @param todayTeam 오늘 인증 의무가 있는 날(진행 중, 기간 안)만 값이 있다
	 * @param myTodayStatus 오늘 내 인증 상태. 제출 전이면 null
	 * @param pendingJudgeCount 내가 판정할 PENDING 인증 수. 판정 권한이 없으면 0
	 */
	record CardStats(BigDecimal teamAchievementRate, TodayTeam todayTeam, String myTodayStatus, int pendingJudgeCount) {}

	/** @param photos 오늘 승인된 인증 사진. 제출 시각 순 */
	record TodayTeam(int certifiedCount, int targetCount, List<TodayPhoto> photos) {}

	/** @param thumbnailUrl 서명된 주소. 만들 수 없으면 null */
	record TodayPhoto(long userId, String thumbnailUrl, Instant submittedAt) {}
}

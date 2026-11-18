package com.morak.feature.challenge.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** 내 챌린지 목록에 필요한 인증 집계를 certification 도메인에서 읽는 포트. */
public interface ChallengeCertificationReader {

	/**
	 * participationId -> 인증 집계. 집계할 수 없는 참가는 결과에 없고, 응답에서는 해당 필드를 null로 내려준다.
	 *
	 * @param today 집계 기준일(KST)
	 */
	Map<Long, CertificationStats> findStats(Collection<Long> participationIds, LocalDate today);

	/**
	 * 참가 한 건의 인증 집계.
	 *
	 * <p>TODO(D-22): 필드 구성은 프론트와 확정 전 제안값이다. 팀 달성률은 위키 §4.5 FRD 식 (대상 − 실패) ÷ 대상이고, 대상은 어제(KST)까지의
	 * 인증 의무 횟수로 센다.
	 *
	 * @param teamAchievementRate 팀 달성률(0~100, 소수 첫째 자리). 집계 대상이 없으면 null
	 * @param todayCertifiedCount 오늘 인증을 제출한 팀원 수
	 * @param todayTargetCount 오늘 인증 의무가 있는 팀원 수
	 * @param myTodayStatus 오늘 내 인증 상태(certification.status). 제출 전이면 null
	 * @param pendingJudgeCount 내가 판정해야 하는 미처리 인증 수(홈 '판정하기'). 판정 권한이 없으면 0
	 */
	record CertificationStats(BigDecimal teamAchievementRate, Integer todayCertifiedCount, Integer todayTargetCount,
			String myTodayStatus, Integer pendingJudgeCount) {}
}

package com.morak.feature.challenge.application.dto.result;

import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 내 챌린지 목록 한 행.
 *
 * <p>TODO(D-22): 필드명은 프론트와 확정 전 제안값이다. 인증 관련 필드(teamAchievementRate ~ pendingJudgeCount)는 인증 집계 연동 전까지
 * null이다.
 *
 * @param progressDay 진행 일차(1부터). 진행 중·정산 대기일 때만 값이 있다
 * @param startHeld 시작 보류(START_HELD) 안내. 그 외 상태면 null
 */
public record MyChallengeItem(long participationId, long challengeId, String title, ChallengeCategory category,
		String coverImageUrl, LocalDate startDate, LocalDate endDate, int pointAmount, String myRole, String myStatus,
		String challengeStatus, int currentCount, int capacity, Integer progressDay, StartHeld startHeld,
		BigDecimal teamAchievementRate, Integer todayCertifiedCount, Integer todayTargetCount, String myTodayStatus,
		Integer pendingJudgeCount) {

	/**
	 * 시작 보류 안내(위키 §4.2).
	 *
	 * @param minCount 시작에 필요한 최소 인원(공개 3, 비공개 2, 방장 포함)
	 * @param recruitDeadline 추가 모집 마감 = 예정 시작일 + 2일 23:59:59 KST. 방장이 기간 재설정·폐기를 고를 수 있는 기한이기도 하다
	 */
	public record StartHeld(int currentCount, int minCount, OffsetDateTime recruitDeadline) {}
}

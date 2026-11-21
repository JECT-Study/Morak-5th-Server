package com.morak.feature.participation.application.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 챌린지 종료 후 결과 조회(위키 §4.5). 정산이 확정한 participation_result를 읽는다. */
public final class ChallengeResultView {

	private ChallengeResultView() {}

	/**
	 * 팀 결과. 팀 달성률은 참가자별 결과를 합산한 값이다(하루 전체를 하나로 세지 않는다).
	 *
	 * @param teamApprovedCount 팀 전체 성공 인증 횟수(분자)
	 * @param teamTotalCount 팀 전체 인증 대상 횟수(분모). 퇴장한 날부터의 의무는 정산이 이미 뺀 값이다
	 * @param teamAchievementRate 팀 달성률(%). 소수 첫째 자리 half-up
	 * @param members 참가자별 결과. 인증률이 높은 순
	 */
	public record Team(
			int teamApprovedCount, int teamTotalCount, BigDecimal teamAchievementRate, List<Member> members) {}

	/**
	 * 팀원 한 명의 결과.
	 *
	 * @param failedCount 실패 횟수(인증 대상 − 성공)
	 * @param expelled 퇴장한 참가자인지. 화면이 퇴장자 표시를 한다
	 */
	public record Member(
			long participationId,
			long userId,
			String nickname,
			String profileImageUrl,
			int approvedCount,
			int failedCount,
			BigDecimal achievementRate,
			boolean completed,
			boolean expelled) {}

	/**
	 * 내 결과.
	 * TODO(D-28): 정산 결과(추가 보상·전액 반환·비례 반환·퇴장 0P), 반환·차감 포인트, 이번에 받은 메달은 정산 구현 뒤에 붙인다.
	 *
	 * @param achievementRate 내 성공률(%)
	 * @param teamAchievementRate 팀 달성률(%)
	 */
	public record Mine(
			long participationId,
			int approvedCount,
			int totalCount,
			BigDecimal achievementRate,
			BigDecimal teamAchievementRate,
			boolean completed,
			boolean expelled,
			int currentStreak,
			int maxStreak,
			Instant finalizedAt) {}
}

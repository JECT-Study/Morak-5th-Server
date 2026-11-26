package com.morak.feature.participation.application.dto.result;

import java.time.Instant;
import java.util.List;

/**
 * 방장이 보는 신청 한 건.
 *
 * @param status PENDING, INVALIDATED(한도 초과로 무효, 확인 전), ACTIVE, REJECTED
 * @param message 신청자의 각오 한마디. 없으면 null
 */
public record ApplicationView(
		long participationId, String status, String message, Instant appliedAt, Applicant applicant) {

	/**
	 * 신청자 프로필(위키 §4.1).
	 *
	 * @param certificationRate 개인 인증률(정수 %, 소수 첫째 자리 half-up). 인증 기록이 없으면 null
	 * @param morakiStage 모락이 단계(1~5). 인증 기록이 없으면 null이고 기본 캐릭터를 보여 준다
	 * @param completedCount 누적 완주 횟수
	 * @param midwayFailCount 중도 실패(퇴장) 챌린지 횟수
	 */
	public record Applicant(
			long userId,
			String nickname,
			String profileImageUrl,
			Integer certificationRate,
			Integer morakiStage,
			int completedCount,
			int midwayFailCount,
			List<Medal> medals) {}

	public record Medal(String code, String name, String iconUrl) {}
}

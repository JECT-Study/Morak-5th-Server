package com.morak.feature.participation.application.dto.result;

/**
 * 내 기록(프로필 통계, 위키 §4.1).
 *
 * @param certificationRate 개인 인증률(정수 %, 소수 첫째 자리 half-up). 인증 기록이 없으면 null
 * @param morakiStage 모락이 단계(1~5). 인증 기록이 없으면 null이고 기본 캐릭터를 보여 준다
 * @param hasCertificationRecord 인증 기록이 있는지. false면 "인증 기록 없음"을 보여 준다
 * @param completedCount 누적 완주 횟수
 * @param midwayFailCount 중도 실패(퇴장) 챌린지 횟수
 */
public record MyRecordView(
		Integer certificationRate,
		Integer morakiStage,
		boolean hasCertificationRecord,
		int completedCount,
		int midwayFailCount) {}

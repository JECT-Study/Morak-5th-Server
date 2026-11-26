package com.morak.feature.certification.application.port.out;

import java.time.LocalDate;
import java.util.List;

/** 퇴장 판정(위키 §4.4, §4.9 ⑥)에 쓰는 실패 횟수를 읽는다. */
public interface CertificationExpelStore {

	/**
	 * 포인트 없는 진행 중 챌린지(오늘이 종료일 이전)의 ACTIVE 참가자마다 {@code today} 전날까지의 실패 횟수(FAILED·REJECTED)를 센다.
	 * 실패가 없는 참가자는 빠진다. 오늘 날짜 인증은 세지 않는다(D-12).
	 */
	List<FailCount> findFailCounts(LocalDate today);

	record FailCount(long challengeId, String title, LocalDate startDate, LocalDate endDate, long participationId,
			int failCount) {}
}

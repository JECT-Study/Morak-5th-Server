package com.morak.feature.certification.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 퇴장 판정(위키 §4.4, §4.9 ⑥·⑦)에 쓰는 실패 횟수를 읽는다. */
public interface CertificationExpelStore {

	/**
	 * 포인트 없는 진행 중 챌린지(오늘이 종료일 이전)의 ACTIVE 참가자마다 {@code today} 전날까지의 실패 횟수(FAILED·REJECTED)를 센다.
	 * 실패가 없는 참가자는 빠진다. 오늘 날짜 인증은 세지 않는다(D-12).
	 */
	List<FailCount> findFailCounts(LocalDate today);

	/**
	 * 포인트 진행 중 챌린지(오늘이 종료일 이전)의 ACTIVE 참가자마다 {@code through}까지의 실패 횟수를 센다(⑦). 조건에 맞지 않는 챌린지면
	 * 비어 있다.
	 */
	List<FailCount> findReviewedFailCounts(long challengeId, LocalDate through, LocalDate today);

	/** 챌린지의 ACTIVE 참가자 인증 중 가장 이른 미판정(PENDING) 날짜. */
	Optional<LocalDate> findEarliestPendingDate(long challengeId);

	record FailCount(long challengeId, String title, LocalDate startDate, LocalDate endDate, int pointAmount,
			long participationId, int failCount) {}
}

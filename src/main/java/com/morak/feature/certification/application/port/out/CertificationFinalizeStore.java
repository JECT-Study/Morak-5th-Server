package com.morak.feature.certification.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** 결과 확정(위키 §4.5, §4.9 ⑧)에 쓰는 대상 챌린지와 참가자별 인증을 읽는다. */
public interface CertificationFinalizeStore {

	/**
	 * 포인트 없는 챌린지 중 결과 확정 대상: 종료일이 {@code today}보다 앞인 IN_PROGRESS와 FINALIZING(조기 종료·확정이 밀린 건).
	 * 챌린지 ID 오름차순.
	 */
	List<Long> findCandidates(LocalDate today);

	/**
	 * 결과를 남길 참가자(ACTIVE·COMPLETED·EXPELLED·EARLY_ENDED)마다 의무 기간과 그 안의 인증을 읽는다. 마지막 의무일 =
	 * min(종료일, 끝난 날(KST) − 1)이라 퇴장일·조기 종료일부터는 빠진다(위키 §4.5 팀 달성률, D-44).
	 */
	List<Member> findMembers(long challengeId);

	/**
	 * @param active 아직 ACTIVE(결과 확정 때 완주)인지
	 * @param successDates 의무 기간 안의 APPROVED·AUTO_APPROVED 인증 날짜
	 * @param pendingCount 의무 기간 안의 판정 대기(PENDING) 수
	 */
	record Member(long participationId, boolean active, LocalDate startDate, LocalDate lastDay,
			Set<LocalDate> successDates, int pendingCount) {}
}

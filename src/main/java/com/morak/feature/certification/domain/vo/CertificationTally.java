package com.morak.feature.certification.domain.vo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * 결과 확정 때 참가자 한 명의 인증 집계(위키 §4.5). 의무 기간은 시작일~마지막 의무일(양 끝 포함)이고, 성공은
 * APPROVED·AUTO_APPROVED다.
 *
 * <p>TODO(D-47): 연속 인증 일수는 위키에 규칙이 없어 제안값이다. 의무일마다 성공이면 1일씩 이어지고 실패(미제출·거절)면 끊긴다.
 * {@code maxStreak}은 가장 긴 연속, {@code currentStreak}은 마지막 의무일까지 이어진 연속이다.
 *
 * @param approvedCount 의무 기간 안의 성공 수
 * @param totalCount 의무 일수. 마지막 의무일이 시작일보다 앞이면(시작일에 퇴장) 0
 */
public record CertificationTally(int approvedCount, int totalCount, int currentStreak, int maxStreak) {

	public static CertificationTally of(LocalDate startDate, LocalDate lastDay, Set<LocalDate> successDates) {
		int total = (int) Math.max(0, ChronoUnit.DAYS.between(startDate, lastDay) + 1);
		int approved = 0;
		int run = 0;
		int max = 0;
		for (int i = 0; i < total; i++) {
			if (successDates.contains(startDate.plusDays(i))) {
				approved++;
				run++;
				max = Math.max(max, run);
			} else {
				run = 0;
			}
		}
		return new CertificationTally(approved, total, run, max);
	}
}

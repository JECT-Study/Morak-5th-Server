package com.morak.feature.certification.domain.vo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 퇴장 기준 실패 횟수 = ceil(예정 인증 일수 × 15%), 정수 연산 {@code (N*15+99)/100} (위키 §4.4 퇴장, §6). */
public final class ExpelThreshold {

	private ExpelThreshold() {}

	public static int of(int scheduledDays) {
		return (scheduledDays * 15 + 99) / 100;
	}

	/** 시작일~종료일(양 끝 포함)을 예정 인증 일수로 본다. */
	public static int of(LocalDate startDate, LocalDate endDate) {
		return of((int) ChronoUnit.DAYS.between(startDate, endDate) + 1);
	}
}

package com.morak.feature.certification.domain.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** 운영진 검수 마감 = 인증일 다음 날 12:00 KST(위키 §4.4 판정 담당, §4.10 [확정, FRD 11/1]). 지나도 자동 승인하지 않는다. */
public final class ReviewDeadline {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final LocalTime DUE_TIME = LocalTime.NOON;

	private ReviewDeadline() {}

	public static Instant of(LocalDate certDate) {
		return certDate.plusDays(1).atTime(DUE_TIME).atZone(KST).toInstant();
	}
}

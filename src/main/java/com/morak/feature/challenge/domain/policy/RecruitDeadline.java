package com.morak.feature.challenge.domain.policy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 시작 보류 후 추가 모집 마감(위키 §4.2). 예정 시작일을 1일째로 센 3일째 23:59까지이며, 컬럼이 아니라 시작일에서 계산한다(§5).
 * 시작 처리와 내 챌린지 목록이 같은 기준을 쓰도록 한곳에 둔다.
 */
public final class RecruitDeadline {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private RecruitDeadline() {}

	/** 마감 날짜(예정 시작일 + 2일). 그 날 23:59까지다. */
	public static LocalDate dateOf(LocalDate startDate) {
		return startDate.plusDays(2);
	}

	/** 추가 모집이 끝나는 시각(마감일 다음 날 00:00 KST). 이 시각 전에 합류한 인원만 시작 인원으로 센다. */
	public static Instant endOf(LocalDate startDate) {
		return dateOf(startDate).plusDays(1).atStartOfDay(KST).toInstant();
	}
}

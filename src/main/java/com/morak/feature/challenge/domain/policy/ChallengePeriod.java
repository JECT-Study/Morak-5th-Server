package com.morak.feature.challenge.domain.policy;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** 챌린지 기간 규칙(위키 §4.2). 개설과 시작 보류 기간 재설정이 함께 쓴다. */
public final class ChallengePeriod {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final LocalDate MIN_STORABLE_DATE = LocalDate.of(-4713, 11, 24);
	private static final LocalDate MAX_STORABLE_DATE = LocalDate.of(5874897, 12, 31);

	private ChallengePeriod() {}

	/** PostgreSQL DATE의 저장 범위와 최소 7일(`end_date >= start_date + 6`)을 확인한다. 기간 상한은 미정(O-4)이라 두지 않는다. */
	public static void validate(LocalDate startDate, LocalDate endDate) {
		if (outsideStorageRange(startDate) || outsideStorageRange(endDate)) {
			throw new BusinessException(ChallengeErrorCode.DATE_OUT_OF_RANGE);
		}
		if (ChronoUnit.DAYS.between(startDate, endDate) < 6) {
			throw new BusinessException(ChallengeErrorCode.INVALID_PERIOD);
		}
	}

	/** 주어진 시각의 KST 날짜보다 시작일이 뒤(내일부터)인지 확인한다. */
	public static void validateStartDate(LocalDate startDate, Instant now) {
		if (!startDate.isAfter(LocalDate.ofInstant(now, KST))) {
			throw new BusinessException(ChallengeErrorCode.INVALID_START_DATE);
		}
	}

	private static boolean outsideStorageRange(LocalDate date) {
		return date.isBefore(MIN_STORABLE_DATE) || date.isAfter(MAX_STORABLE_DATE);
	}
}

package com.morak.feature.challenge.application.dto;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record CreateChallengeCommand(String title, ChallengeVisibility visibility, ChallengeCategory category,
		String description, String purpose, String commonMission, String verificationRules, LocalDate startDate, LocalDate endDate, int capacity,
		int pointAmount, String expectations) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final LocalDate MIN_STORABLE_DATE = LocalDate.of(-4713, 11, 24);
	private static final LocalDate MAX_STORABLE_DATE = LocalDate.of(5874897, 12, 31);

	/** PostgreSQL DATE의 저장 범위와 개설 조건을 검증한다. 날짜 범위는 기간 상한 정책과 별개다. */
	public CreateChallengeCommand {
		if (outsideStorageRange(startDate) || outsideStorageRange(endDate)) {
			throw new BusinessException(ChallengeErrorCode.DATE_OUT_OF_RANGE);
		}
		if (ChronoUnit.DAYS.between(startDate, endDate) < 6) {
			throw new BusinessException(ChallengeErrorCode.INVALID_PERIOD);
		}
		int minimumCapacity = visibility == ChallengeVisibility.PUBLIC ? 3 : 2;
		if (capacity < minimumCapacity || capacity > 10) {
			throw new BusinessException(ChallengeErrorCode.INVALID_CAPACITY);
		}
		if (pointAmount != 0 && (visibility != ChallengeVisibility.PUBLIC || pointAmount < 1000 || pointAmount > 5000)) {
			throw new BusinessException(ChallengeErrorCode.INVALID_POINT_AMOUNT);
		}
	}

	private static boolean outsideStorageRange(LocalDate date) {
		return date.isBefore(MIN_STORABLE_DATE) || date.isAfter(MAX_STORABLE_DATE);
	}

	/** 주어진 시각의 KST 날짜보다 시작일이 뒤인지 확인한다. */
	public void validateStartDate(Instant now) {
		if (!startDate.isAfter(LocalDate.ofInstant(now, KST))) {
			throw new BusinessException(ChallengeErrorCode.INVALID_START_DATE);
		}
	}

	public Challenge toChallenge(long ownerId, String inviteCode, Instant now) {
		return new Challenge(ownerId, title, visibility, category, description, purpose, commonMission, verificationRules,
				expectations, inviteCode, startDate, endDate, capacity, now);
	}
}

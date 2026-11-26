package com.morak.feature.challenge.application.dto.command;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.policy.ChallengePeriod;
import java.time.Instant;
import java.time.LocalDate;

public record CreateChallengeCommand(String title, ChallengeVisibility visibility, ChallengeCategory category,
		String description, String purpose, String commonMission, String verificationRules, LocalDate startDate, LocalDate endDate, int capacity,
		int pointAmount, String expectations) {

	private static final int POINT_AMOUNT_STEP = 500;

	/** PostgreSQL DATE의 저장 범위와 개설 조건을 검증한다. 날짜 범위는 기간 상한 정책과 별개다. */
	public CreateChallengeCommand {
		ChallengePeriod.validate(startDate, endDate);
		int minimumCapacity = visibility == ChallengeVisibility.PUBLIC ? 3 : 2;
		if (capacity < minimumCapacity || capacity > 10) {
			throw new BusinessException(ChallengeErrorCode.INVALID_CAPACITY);
		}
		if (pointAmount != 0 && (visibility != ChallengeVisibility.PUBLIC || pointAmount < 1000
				|| pointAmount > 5000 || pointAmount % POINT_AMOUNT_STEP != 0)) {
			throw new BusinessException(ChallengeErrorCode.INVALID_POINT_AMOUNT);
		}
	}

	/** 주어진 시각의 KST 날짜보다 시작일이 뒤인지 확인한다. */
	public void validateStartDate(Instant now) {
		ChallengePeriod.validateStartDate(startDate, now);
	}

	public Challenge toChallenge(long ownerId, String inviteCode, String coverImageKey) {
		return new Challenge(ownerId, title, visibility, category, description, purpose, commonMission, verificationRules,
				expectations, inviteCode, startDate, endDate, capacity, pointAmount, coverImageKey);
	}
}

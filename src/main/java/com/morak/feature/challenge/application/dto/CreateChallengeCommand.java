package com.morak.feature.challenge.application.dto;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.entity.Challenge.Category;
import com.morak.feature.challenge.domain.entity.Challenge.Visibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record CreateChallengeCommand(String title, Visibility visibility, Category category, String purpose,
		String commonMission, String verificationRules, LocalDate startDate, LocalDate endDate, int capacity,
		int pointAmount, String expectations) {

	public CreateChallengeCommand {
		if (ChronoUnit.DAYS.between(startDate, endDate) < 6) {
			throw new BusinessException(ChallengeErrorCode.INVALID_PERIOD);
		}
		int minimumCapacity = visibility == Visibility.PUBLIC ? 3 : 2;
		if (capacity < minimumCapacity || capacity > 10) {
			throw new BusinessException(ChallengeErrorCode.INVALID_CAPACITY);
		}
		if (pointAmount != 0 && (visibility != Visibility.PUBLIC || pointAmount < 1000 || pointAmount > 5000)) {
			throw new BusinessException(ChallengeErrorCode.INVALID_POINT_AMOUNT);
		}
	}

	public Challenge toChallenge(long ownerId, String inviteCode, Instant now) {
		return new Challenge(ownerId, title, visibility, category, purpose, commonMission, verificationRules,
				expectations, inviteCode, startDate, endDate, capacity, now);
	}
}

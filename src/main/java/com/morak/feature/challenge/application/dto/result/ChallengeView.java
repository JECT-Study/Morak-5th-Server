package com.morak.feature.challenge.application.dto.result;

import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import java.time.LocalDate;

public record ChallengeView(long id, String title, String description, String visibility, ChallengeCategory category,
		String purpose, String commonMission, String verificationRules, String expectations, String status,
		LocalDate startDate, LocalDate endDate, int capacity, int currentCount,
		int pointAmount, String myParticipationStatus, boolean canApply, String coverImageUrl) {}

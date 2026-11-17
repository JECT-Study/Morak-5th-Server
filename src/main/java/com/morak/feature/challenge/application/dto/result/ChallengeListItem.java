package com.morak.feature.challenge.application.dto.result;

import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import java.time.LocalDate;

public record ChallengeListItem(long id, String title, String description, ChallengeCategory category,
		LocalDate startDate, LocalDate endDate, int capacity, int currentCount, int pointAmount, String coverImageUrl) {}

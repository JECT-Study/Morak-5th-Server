package com.morak.feature.challenge.application.dto.query;

import com.morak.feature.challenge.domain.enums.ChallengeCategory;

public record ListChallengesQuery(ChallengeCategory category, String keyword, Long cursor, int size) {}

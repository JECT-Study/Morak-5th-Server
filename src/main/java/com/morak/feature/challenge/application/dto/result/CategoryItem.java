package com.morak.feature.challenge.application.dto.result;

import com.morak.feature.challenge.domain.enums.ChallengeCategory;

public record CategoryItem(ChallengeCategory code, String name) {}

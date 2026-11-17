package com.morak.feature.challenge.application.dto.result;

import java.util.List;

public record ChallengePage(List<ChallengeListItem> items, Long nextCursor, boolean hasNext) {}

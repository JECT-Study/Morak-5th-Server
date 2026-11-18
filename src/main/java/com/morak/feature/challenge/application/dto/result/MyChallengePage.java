package com.morak.feature.challenge.application.dto.result;

import java.util.List;

/** 내 챌린지 목록. 홈은 한 번에 모두 내려주므로 nextCursor가 null, hasNext가 false다. */
public record MyChallengePage(List<MyChallengeItem> items, Long nextCursor, boolean hasNext) {}

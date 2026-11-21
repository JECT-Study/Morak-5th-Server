package com.morak.feature.ad.application.dto.result;

import java.time.Instant;

public record AdRewardSession(String token, long minWatchSeconds, Instant expiresAt, int remainingToday) {}

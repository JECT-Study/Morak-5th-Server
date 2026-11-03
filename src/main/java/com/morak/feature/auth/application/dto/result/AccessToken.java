package com.morak.feature.auth.application.dto.result;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {}

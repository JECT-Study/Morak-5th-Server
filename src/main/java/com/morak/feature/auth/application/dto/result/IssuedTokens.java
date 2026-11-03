package com.morak.feature.auth.application.dto.result;

import java.time.Instant;

public record IssuedTokens(
		String accessToken,
		String refreshToken,
		String tokenType,
		long accessTokenExpiresIn,
		Instant accessTokenExpiresAt,
		boolean onboarded) {}

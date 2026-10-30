package com.morak.feature.auth.application.dto;

public record IssuedTokens(
		String accessToken,
		String refreshToken,
		String tokenType,
		long accessTokenExpiresIn,
		boolean onboarded) {}

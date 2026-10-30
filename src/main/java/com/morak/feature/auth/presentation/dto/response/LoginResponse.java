package com.morak.feature.auth.presentation.dto.response;

import com.morak.feature.auth.application.dto.IssuedTokens;

public record LoginResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long accessTokenExpiresIn,
		boolean onboarded) {

	public static LoginResponse from(IssuedTokens tokens) {
		return new LoginResponse(
				tokens.accessToken(),
				tokens.refreshToken(),
				tokens.tokenType(),
				tokens.accessTokenExpiresIn(),
				tokens.onboarded());
	}
}

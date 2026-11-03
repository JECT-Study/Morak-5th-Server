package com.morak.feature.auth.presentation.dto.response;

import java.time.Instant;
import com.morak.feature.auth.application.dto.result.IssuedTokens;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
		@Schema(description = "서비스 Access Token")
		String accessToken,
		@Schema(description = "서비스 Refresh Token")
		String refreshToken,
		@Schema(description = "인증 토큰 유형", example = "Bearer")
		String tokenType,
		@Schema(description = "Access Token 만료까지 남은 시간(초)", example = "1800")
		long accessTokenExpiresIn,
		@Schema(description = "Access Token expiration time (UTC ISO 8601)", example = "2026-11-02T12:30:00Z")
		Instant accessTokenExpiresAt,
		@Schema(description = "온보딩 완료 여부. false이면 온보딩 화면으로 이동")
		boolean onboarded) {

	public static LoginResponse from(IssuedTokens tokens) {
		return new LoginResponse(
				tokens.accessToken(),
				tokens.refreshToken(),
				tokens.tokenType(),
				tokens.accessTokenExpiresIn(),
				tokens.accessTokenExpiresAt(),
				tokens.onboarded());
	}
}

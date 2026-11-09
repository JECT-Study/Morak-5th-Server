package com.morak.feature.auth.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
		@Schema(description = "로그인 또는 재발급으로 받은 Refresh Token. Authorization 헤더 없이 JSON 본문으로 전달합니다.")
		@NotBlank String refreshToken) {}

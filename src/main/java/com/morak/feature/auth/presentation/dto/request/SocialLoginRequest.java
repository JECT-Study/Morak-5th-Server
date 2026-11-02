package com.morak.feature.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record SocialLoginRequest(
		@Schema(description = "카카오 로그인 callback에서 받은 일회용 인가 코드", example = "authorization-code")
		@NotBlank String code) {}

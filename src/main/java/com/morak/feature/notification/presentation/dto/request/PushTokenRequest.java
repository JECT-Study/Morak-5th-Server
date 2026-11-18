package com.morak.feature.notification.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PushTokenRequest(
		@Schema(description = "Expo 푸시 토큰", example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]")
		@NotBlank(message = "토큰은 필수입니다.")
		String token) {}

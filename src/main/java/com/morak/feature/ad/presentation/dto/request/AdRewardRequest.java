package com.morak.feature.ad.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AdRewardRequest(
		@Schema(description = "광고 시청 시작 API로 받은 시청 토큰")
		@NotBlank String token) {}

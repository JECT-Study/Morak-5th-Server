package com.morak.feature.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(@NotBlank String code) {}

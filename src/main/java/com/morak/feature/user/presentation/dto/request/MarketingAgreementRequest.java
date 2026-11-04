package com.morak.feature.user.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

public record MarketingAgreementRequest(@NotNull Boolean marketingAgreed) {}

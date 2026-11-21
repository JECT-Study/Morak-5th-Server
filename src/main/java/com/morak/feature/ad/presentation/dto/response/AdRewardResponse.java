package com.morak.feature.ad.presentation.dto.response;

import com.morak.feature.ad.application.dto.result.AdRewardResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdRewardResponse(
		@Schema(description = "지급한 포인트 (P)", example = "500")
		long amount,
		@Schema(description = "지급 후 잔액 (P)", example = "10500")
		long balance,
		@Schema(description = "오늘 더 받을 수 있는 횟수", example = "4")
		int remainingToday) {

	public static AdRewardResponse from(AdRewardResult result) {
		return new AdRewardResponse(result.amount(), result.balance(), result.remainingToday());
	}
}

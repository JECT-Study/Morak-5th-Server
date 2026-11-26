package com.morak.feature.ad.presentation.dto.response;

import com.morak.feature.ad.application.dto.result.AdRewardSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AdRewardSessionResponse(
		@Schema(description = "시청 토큰. 시청을 마친 뒤 보상 요청에 그대로 보낸다 (1회용)")
		String token,
		@Schema(description = "이 시간(초) 이상 시청해야 보상을 받을 수 있다", example = "5")
		long minWatchSeconds,
		@Schema(description = "토큰 만료 시각")
		Instant expiresAt,
		@Schema(description = "오늘 더 받을 수 있는 횟수", example = "5")
		int remainingToday) {

	public static AdRewardSessionResponse from(AdRewardSession session) {
		return new AdRewardSessionResponse(
				session.token(), session.minWatchSeconds(), session.expiresAt(), session.remainingToday());
	}
}

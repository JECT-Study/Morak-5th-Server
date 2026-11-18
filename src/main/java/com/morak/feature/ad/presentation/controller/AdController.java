package com.morak.feature.ad.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.ad.application.service.ClaimAdRewardService;
import com.morak.feature.ad.application.service.StartAdRewardService;
import com.morak.feature.ad.presentation.dto.request.AdRewardRequest;
import com.morak.feature.ad.presentation.dto.response.AdRewardResponse;
import com.morak.feature.ad.presentation.dto.response.AdRewardSessionResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ads")
@Tag(name = "광고", description = "광고 시청 보상 API (현재 모의 광고)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdController {

	private final StartAdRewardService startService;
	private final ClaimAdRewardService claimService;

	public AdController(StartAdRewardService startService, ClaimAdRewardService claimService) {
		this.startService = startService;
		this.claimService = claimService;
	}

	@PostMapping("/reward-sessions")
	@Operation(summary = "광고 시청 시작",
			description = "1회용 시청 토큰을 발급합니다. 시청을 시작만 해서는 횟수가 차감되지 않습니다. "
					+ "오늘 보상을 5회 모두 받았으면 거절합니다.")
	public ApiResponse<AdRewardSessionResponse> start(@AuthenticationPrincipal LoginUser loginUser) {
		return ApiResponse.success(AdRewardSessionResponse.from(startService.start(loginUser.userId().value())));
	}

	@PostMapping("/rewards")
	@Operation(summary = "광고 시청 완료(모의 광고)",
			description = "시청 토큰을 확인하고 500P를 지급합니다. 같은 토큰으로 다시 요청해도 한 번만 지급됩니다. "
					+ "하루 최대 5회(KST 자정 초기화)이고, 앱·웹 구분 없이 합산합니다.")
	public ApiResponse<AdRewardResponse> claim(
			@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody AdRewardRequest request) {
		return ApiResponse.success(AdRewardResponse.from(claimService.claim(loginUser.userId().value(), request.token())));
	}
}

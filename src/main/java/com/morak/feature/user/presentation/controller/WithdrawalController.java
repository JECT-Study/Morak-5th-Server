package com.morak.feature.user.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.user.application.service.WithdrawUserService;
import com.morak.feature.user.domain.vo.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class WithdrawalController {

	private final WithdrawUserService withdrawUserService;

	public WithdrawalController(WithdrawUserService withdrawUserService) {
		this.withdrawUserService = withdrawUserService;
	}

	@DeleteMapping("/me")
	@Operation(summary = "회원 탈퇴",
			description = "진행 중이거나 시작을 기다리는 챌린지(방장 포함)가 있으면 409(USER_005)로 거절합니다. 탈퇴하면 개인정보가 비식별화되고 모든 로그인 세션이 끊깁니다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ApiResponse<Map<String, Object>> withdraw(@AuthenticationPrincipal LoginUser loginUser) {
		withdrawUserService.withdraw(new UserId(loginUser.userId().value()));
		return ApiResponse.successEmpty();
	}
}

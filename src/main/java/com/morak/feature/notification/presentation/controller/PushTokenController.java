package com.morak.feature.notification.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.notification.application.service.PushTokenService;
import com.morak.feature.notification.presentation.dto.request.PushTokenRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/push-tokens")
@Tag(name = "푸시 토큰", description = "앱의 푸시 토큰 저장·삭제 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PushTokenController {

	private final PushTokenService pushTokenService;

	public PushTokenController(PushTokenService pushTokenService) {
		this.pushTokenService = pushTokenService;
	}

	@PutMapping
	@Operation(summary = "푸시 토큰 저장",
			description = "로그인한 사용자의 Expo 푸시 토큰을 저장합니다. 같은 토큰이 이미 있으면 지금 로그인한 사용자의 것으로 "
					+ "덮어쓰므로 여러 번 호출해도 결과가 같습니다.")
	public ApiResponse<Map<String, Object>> register(
			@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody PushTokenRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		pushTokenService.register(loginUser.userId().value(), request.token(), userAgent);
		return ApiResponse.successEmpty();
	}

	@DeleteMapping
	@Operation(summary = "푸시 토큰 삭제",
			description = "로그아웃할 때 호출합니다. 본인 토큰만 지우며, 이미 없어도 성공합니다.")
	public ApiResponse<Map<String, Object>> unregister(
			@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody PushTokenRequest request) {
		pushTokenService.unregister(loginUser.userId().value(), request.token());
		return ApiResponse.successEmpty();
	}
}

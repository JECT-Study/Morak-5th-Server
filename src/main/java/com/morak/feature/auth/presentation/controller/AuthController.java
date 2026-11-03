package com.morak.feature.auth.presentation.controller;

import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.command.SocialLoginCommand;
import com.morak.feature.auth.application.service.KakaoLoginService;
import com.morak.feature.auth.presentation.dto.request.SocialLoginRequest;
import com.morak.feature.auth.presentation.dto.response.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/auth/login")
@Tag(name = "인증", description = "소셜 로그인 API")
public class AuthController {

	private final KakaoLoginService kakaoLoginService;

	public AuthController(KakaoLoginService kakaoLoginService) {
		this.kakaoLoginService = kakaoLoginService;
	}

	@PostMapping("/{provider}")
	@Operation(
			summary = "소셜 로그인",
			description = "Next.js BFF가 OAuth callback에서 받은 인가 코드를 전달합니다. 현재 kakao provider를 지원하며, 로그인 성공 시 서비스 토큰과 온보딩 여부를 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공. Access Token, Refresh Token, 만료 시간, 온보딩 여부를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "지원하지 않는 provider이거나 요청의 인가 코드가 비어 있습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인가 코드가 만료되었거나 이미 사용되었습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "카카오 OAuth 서버와 통신할 수 없습니다.")
	})
	public ResponseEntity<ApiResponse<LoginResponse>> login(
			@Parameter(description = "소셜 로그인 제공자. 현재 kakao만 지원합니다.", example = "kakao") @PathVariable String provider,
			@Valid @RequestBody SocialLoginRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(LoginResponse.from(kakaoLoginService.login(new SocialLoginCommand(provider, request.code())))));
	}
}

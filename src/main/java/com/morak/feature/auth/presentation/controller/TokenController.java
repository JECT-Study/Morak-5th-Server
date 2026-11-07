package com.morak.feature.auth.presentation.controller;

import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.service.LogoutService;
import com.morak.feature.auth.application.service.RefreshTokenService;
import com.morak.feature.auth.presentation.dto.request.RefreshTokenRequest;
import com.morak.feature.auth.presentation.dto.response.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "인증", description = "소셜 로그인 및 토큰 관리 API")
public class TokenController {
	private final RefreshTokenService refreshTokenService;
	private final LogoutService logoutService;

	public TokenController(RefreshTokenService refreshTokenService, LogoutService logoutService) {
		this.refreshTokenService = refreshTokenService;
		this.logoutService = logoutService;
	}

	@PostMapping("/refresh")
	@Operation(summary = "토큰 재발급", description = "Access Token 없이 Refresh Token으로 새 토큰 쌍과 최신 온보딩 상태를 받습니다. 기존 Refresh Token은 폐기되므로 응답의 새 토큰으로 교체해야 합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공. 로그인과 동일한 토큰 응답을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본문 또는 refreshToken이 누락되었거나 비어 있습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_007: 미등록·만료·폐기된 토큰이거나 회원이 없습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_003: 탈퇴한 회원입니다.")
	})
	public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(LoginResponse.from(refreshTokenService.refresh(request.refreshToken()))));
	}

	@PostMapping("/logout")
	@Operation(summary = "로그아웃", description = "Access Token 없이 전달된 Refresh Token만 폐기합니다. 다른 기기의 로그인은 유지되며 기존 Access Token은 만료까지 유효합니다. 미등록·만료·이미 폐기된 토큰도 성공으로 처리합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 완료. 빈 성공 데이터를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본문 또는 refreshToken이 누락되었거나 비어 있습니다.")
	})
	public ResponseEntity<ApiResponse<Map<String, Object>>> logout(@Valid @RequestBody RefreshTokenRequest request) {
		logoutService.logout(request.refreshToken());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.successEmpty());
	}
}

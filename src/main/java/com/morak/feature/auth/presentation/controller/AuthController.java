package com.morak.feature.auth.presentation.controller;

import com.morak.common.response.ApiResponse;
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

@RestController
@RequestMapping("/auth/login")
public class AuthController {

	private final KakaoLoginService kakaoLoginService;

	public AuthController(KakaoLoginService kakaoLoginService) {
		this.kakaoLoginService = kakaoLoginService;
	}

	@PostMapping("/{provider}")
	public ResponseEntity<ApiResponse<LoginResponse>> login(
			@PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(LoginResponse.from(kakaoLoginService.login(provider, request.code()))));
	}
}

package com.morak.feature.auth.presentation.controller;

import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.service.KakaoLoginService;
import com.morak.feature.auth.application.service.SocialOAuthProviderRegistry;
import com.morak.feature.auth.presentation.dto.request.KakaoLoginRequest;
import com.morak.feature.auth.presentation.dto.response.LoginResponse;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/auth/login/kakao")
public class AuthController {

	private final KakaoLoginService kakaoLoginService;
	private final SocialOAuthProviderRegistry providerRegistry;
	private final boolean stateCookieSecure;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthController(
			KakaoLoginService kakaoLoginService,
			SocialOAuthProviderRegistry providerRegistry,
			@Value("${morak.auth.oauth.state-cookie-secure:false}") boolean stateCookieSecure) {
		this.kakaoLoginService = kakaoLoginService;
		this.providerRegistry = providerRegistry;
		this.stateCookieSecure = stateCookieSecure;
	}

	@GetMapping
	public ResponseEntity<Void> beginKakaoLogin() {
		byte[] stateBytes = new byte[32];
		secureRandom.nextBytes(stateBytes);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
		ResponseCookie stateCookie = ResponseCookie.from("kakao_oauth_state", state)
				.httpOnly(true)
				.secure(stateCookieSecure)
				.sameSite("Lax")
				.path("/auth/login/kakao/callback")
				.maxAge(java.time.Duration.ofMinutes(5))
				.build();
		return ResponseEntity.status(302)
				.header(HttpHeaders.LOCATION, providerRegistry.get("KAKAO").authorizationUri(state))
				.header(HttpHeaders.SET_COOKIE, stateCookie.toString())
				.build();
	}

	@PostMapping
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody KakaoLoginRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(LoginResponse.from(kakaoLoginService.login(request.code()))));
	}

	@GetMapping("/callback")
	public ResponseEntity<ApiResponse<LoginResponse>> callback(
			@RequestParam String code,
			@RequestParam String state,
			@CookieValue("kakao_oauth_state") String expectedState) {
		ResponseCookie clearStateCookie = ResponseCookie.from("kakao_oauth_state", "")
				.httpOnly(true)
				.secure(stateCookieSecure)
				.sameSite("Lax")
				.path("/auth/login/kakao/callback")
				.maxAge(java.time.Duration.ZERO)
				.build();
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.SET_COOKIE, clearStateCookie.toString())
				.body(ApiResponse.success(LoginResponse.from(
						kakaoLoginService.loginFromCallback(code, state, expectedState))));
	}
}

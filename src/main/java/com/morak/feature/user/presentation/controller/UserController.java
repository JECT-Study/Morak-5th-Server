package com.morak.feature.user.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.user.application.dto.result.MyUserInfo;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.domain.vo.Nickname;
import com.morak.feature.user.presentation.dto.request.MarketingAgreementRequest;
import com.morak.feature.user.presentation.dto.request.OnboardingRequest;
import com.morak.feature.user.presentation.dto.request.TermsAgreementsRequest;
import com.morak.feature.user.presentation.dto.response.NicknameCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/nickname-check")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ApiResponse<NicknameCheckResponse> checkNickname(
			@Parameter(required = true) @RequestParam(name = "nickname", required = false) String nickname) {
		boolean available = userService.isNicknameAvailable(new Nickname(nickname));
		return ApiResponse.success(new NicknameCheckResponse(available));
	}

	@PostMapping("/me/terms-agreements")
	@Operation(summary = "최초 약관 동의",
			description = "필수 항목 4개가 모두 true여야 합니다. 초기 마케팅 수신 선택도 함께 저장합니다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ApiResponse<Map<String, Object>> agreeToTerms(
			@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody TermsAgreementsRequest request) {
		userService.agreeToTerms(loginUser.userId(), request.toAgreements());
		return ApiResponse.successEmpty();
	}

	@PatchMapping("/me/marketing-agreements")
	@Operation(summary = "마케팅 수신 동의 변경", description = "최초 약관 동의를 완료한 회원의 마케팅 수신 동의를 변경합니다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ApiResponse<Map<String, Object>> updateMarketingAgreement(
			@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody MarketingAgreementRequest request) {
		userService.updateMarketingAgreement(loginUser.userId(), request.marketingAgreed());
		return ApiResponse.successEmpty();
	}

	@GetMapping("/me")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ResponseEntity<ApiResponse<MyUserInfo>> getMe(@AuthenticationPrincipal LoginUser loginUser) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(userService.getMyInfo(loginUser.userId())));
	}

	@PostMapping("/me/onboarding")
	@Operation(summary = "온보딩 완료")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ResponseEntity<ApiResponse<MyUserInfo>> completeOnboarding(
			@AuthenticationPrincipal LoginUser loginUser, @RequestBody OnboardingRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(userService.completeOnboarding(loginUser.userId(), request.toProfile())));
	}
}

package com.morak.feature.user.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.exception.BusinessException;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.user.domain.vo.UserId;
import com.morak.feature.user.application.dto.result.MyUserInfo;
import com.morak.feature.user.application.service.ProfileImageService;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.vo.Nickname;
import com.morak.feature.user.presentation.dto.request.MarketingAgreementRequest;
import com.morak.feature.user.presentation.dto.request.OnboardingRequest;
import com.morak.feature.user.presentation.dto.request.TermsAgreementsRequest;
import com.morak.feature.user.presentation.dto.response.NicknameCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;
	private final ProfileImageService profileImageService;

	public UserController(UserService userService, ProfileImageService profileImageService) {
		this.userService = userService;
		this.profileImageService = profileImageService;
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
		userService.agreeToTerms(new UserId(loginUser.userId().value()), request.toAgreements());
		return ApiResponse.successEmpty();
	}

	@PatchMapping("/me/marketing-agreements")
	@Operation(summary = "마케팅 수신 동의 변경", description = "최초 약관 동의를 완료한 회원의 마케팅 수신 동의를 변경합니다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ApiResponse<Map<String, Object>> updateMarketingAgreement(
			@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody MarketingAgreementRequest request) {
		userService.updateMarketingAgreement(new UserId(loginUser.userId().value()), request.marketingAgreed());
		return ApiResponse.successEmpty();
	}

	@GetMapping("/me")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ResponseEntity<ApiResponse<MyUserInfo>> getMe(@AuthenticationPrincipal LoginUser loginUser) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(userService.getMyInfo(new UserId(loginUser.userId().value()))));
	}

	@PostMapping("/me/onboarding")
	@Operation(summary = "온보딩 완료")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ResponseEntity<ApiResponse<MyUserInfo>> completeOnboarding(
			@AuthenticationPrincipal LoginUser loginUser, @RequestBody OnboardingRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(userService.completeOnboarding(new UserId(loginUser.userId().value()), request.toProfile())));
	}

	@PatchMapping("/me")
	@Operation(summary = "프로필 수정",
			description = "닉네임·한 줄 소개·관련 링크·관심 분야를 요청 값으로 바꿉니다. 소개·링크를 비우면 지워집니다. 프로필 사진은 포함하지 않습니다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ResponseEntity<ApiResponse<MyUserInfo>> updateProfile(
			@AuthenticationPrincipal LoginUser loginUser, @RequestBody OnboardingRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(userService.updateProfile(new UserId(loginUser.userId().value()), request.toProfile())));
	}

	// TODO(D-33): 경로와 파트 이름은 프론트와 확정 전 제안값이다.
	@PutMapping(path = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "프로필 사진 변경",
			description = "profileImage는 5MB 이하 JPEG·PNG·WebP 사진 한 장입니다. 응답의 profileImageUrl은 공개 URL입니다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ResponseEntity<ApiResponse<MyUserInfo>> changeProfileImage(
			@AuthenticationPrincipal LoginUser loginUser, @RequestPart("profileImage") List<MultipartFile> photos) {
		if (photos.size() != 1) {
			throw new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE);
		}
		try {
			MyUserInfo result = profileImageService.change(
					new UserId(loginUser.userId().value()), photos.getFirst().getBytes());
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
		} catch (IOException exception) {
			throw new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE);
		}
	}

	// TODO(D-33): 경로는 프론트와 확정 전 제안값이다.
	@DeleteMapping("/me/profile-image")
	@Operation(summary = "프로필 사진 삭제", description = "사진을 지우고 profileImageUrl을 null로 돌립니다. 화면은 기본 캐릭터를 보여 줍니다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ResponseEntity<ApiResponse<MyUserInfo>> removeProfileImage(@AuthenticationPrincipal LoginUser loginUser) {
		MyUserInfo result = profileImageService.remove(new UserId(loginUser.userId().value()));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
	}
}

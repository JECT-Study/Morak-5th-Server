package com.morak.feature.user.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.user.application.dto.result.UserProfileView;
import com.morak.feature.user.application.service.ReadUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 타인 프로필. /users/me 등 고정 경로는 Spring이 이 경로보다 먼저 고른다. */
@RestController
@RequestMapping("/users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserProfileController {

	private final ReadUserProfileService service;

	public UserProfileController(ReadUserProfileService service) {
		this.service = service;
	}

	// TODO(D-32): 응답 필드명은 프론트와 확정 전 제안값이다.
	@GetMapping("/{userId}")
	@Operation(summary = "타인 프로필",
			description = "닉네임, 프로필 사진, 한 줄 소개, 관련 링크, 관심 분야, 개인 인증률, 모락이 단계, 획득 메달, "
					+ "누적 완주·중도 실패 횟수를 돌려줍니다. 인증 기록이 없으면 인증률·모락이 단계는 null입니다. "
					+ "없거나 탈퇴했거나 온보딩 전인 사용자는 404입니다.")
	public ResponseEntity<ApiResponse<UserProfileView>> read(@PathVariable long userId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(service.read(userId)));
	}
}

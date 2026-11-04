package com.morak.feature.user.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.user.application.dto.result.MyUserInfo;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.domain.vo.Nickname;
import com.morak.feature.user.presentation.dto.response.NicknameCheckResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

	@GetMapping("/me")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ResponseEntity<ApiResponse<MyUserInfo>> getMe(@AuthenticationPrincipal LoginUser loginUser) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(userService.getMyInfo(loginUser.userId())));
	}
}

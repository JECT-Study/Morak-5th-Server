package com.morak.feature.challenge.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.exception.CommonErrorCode;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.challenge.application.dto.query.MyChallengeFilter;
import com.morak.feature.challenge.application.dto.result.MyChallengePage;
import com.morak.feature.challenge.application.service.ReadMyChallengesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 로그인한 사용자의 챌린지 목록. 경로는 사용자 리소스 아래지만 응답이 챌린지 카드라 챌린지 도메인이 맡는다. */
@RestController
@RequestMapping("/users/me")
@Tag(name = "내 챌린지", description = "홈·보관함의 내 챌린지 목록 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MyChallengeController {

	private final ReadMyChallengesService service;

	public MyChallengeController(ReadMyChallengesService service) {
		this.service = service;
	}

	// TODO(D-23): 쿼리 이름과 값(status=HOME|ARCHIVE)은 프론트와 확정 전 제안값이다.
	@GetMapping("/participations")
	@Operation(summary = "내 챌린지 목록",
			description = "status=HOME(기본)은 참여 중인 챌린지 전체, ARCHIVE는 퇴장·완주·조기 종료·폐기된 챌린지를 커서로 기본 20개, 최대 100개까지"
					+ " 조회합니다. 팀 달성률·오늘 인증 현황·판정 대기 수는 인증 집계 연동 전까지 null입니다.")
	public ResponseEntity<ApiResponse<MyChallengePage>> list(@AuthenticationPrincipal LoginUser user,
			@RequestParam(defaultValue = "HOME") MyChallengeFilter status,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(service.list(user.userId().value(), status, cursor, size)));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidParameter() {
		var error = CommonErrorCode.INVALID_REQUEST;
		return ResponseEntity.status(error.status()).body(ApiResponse.failure(error.code(), error.message()));
	}
}

package com.morak.feature.participation.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.exception.CommonErrorCode;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.participation.application.dto.query.ApplicationListStatus;
import com.morak.feature.participation.application.service.ReadApplicationsService;
import com.morak.feature.participation.presentation.dto.response.ApplicationListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 방장이 보는 참여 신청 목록. 승인·거절·무효 확인은 {@link ParticipationController}가 맡는다. */
@RestController
@RequestMapping("/challenges/{challengeId}/participation-requests")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ParticipationRequestController {

	private final ReadApplicationsService service;

	public ParticipationRequestController(ReadApplicationsService service) {
		this.service = service;
	}

	// TODO(D-25): 쿼리 값(PENDING 기본, ACTIVE, REJECTED)과 응답 필드명은 프론트와 확정 전 제안값이다.
	@GetMapping
	@Operation(summary = "참여 신청 목록",
			description = "방장만 볼 수 있습니다. status=PENDING(기본)은 승인 대기 신청과 방장이 아직 확인하지 않은 무효 신청(status=INVALIDATED)을,"
					+ " ACTIVE는 승인한 신청을, REJECTED는 거절한 신청을 오래된 순으로 돌려줍니다. 신청마다 각오 한마디와 신청자 프로필"
					+ "(개인 인증률·모락이 단계·메달·완주 횟수·중도 실패 횟수)이 들어 있고, 인증 기록이 없으면 인증률·모락이 단계는 null입니다.")
	public ResponseEntity<ApiResponse<ApplicationListResponse>> list(
			@AuthenticationPrincipal LoginUser user,
			@PathVariable long challengeId,
			@RequestParam(defaultValue = "PENDING") ApplicationListStatus status) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(
						ApplicationListResponse.from(service.list(user.userId().value(), challengeId, status))));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidParameter() {
		var error = CommonErrorCode.INVALID_REQUEST;
		return ResponseEntity.status(error.status()).body(ApiResponse.failure(error.code(), error.message()));
	}
}

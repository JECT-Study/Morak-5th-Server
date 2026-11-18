package com.morak.feature.participation.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.participation.application.service.ApplyParticipationService;
import com.morak.feature.participation.application.service.DecideApplicationService;
import com.morak.feature.participation.presentation.dto.request.ApplyRequest;
import com.morak.feature.participation.presentation.dto.response.AppliedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/challenges/{challengeId}/applications")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ParticipationController {

	private final ApplyParticipationService applyService;
	private final DecideApplicationService decideService;

	public ParticipationController(ApplyParticipationService applyService, DecideApplicationService decideService) {
		this.applyService = applyService;
		this.decideService = decideService;
	}

	@PostMapping
	@Operation(summary = "챌린지 참가 지원",
			description = "지원서를 내고 승인 대기 상태가 됩니다. 포인트가 있는 챌린지는 지원과 동시에 포인트를 걸며, "
					+ "비공개 챌린지는 초대 코드가 필요합니다. 정원이 다 찬 챌린지는 모집 마감이라 409입니다.")
	public ResponseEntity<ApiResponse<AppliedResponse>> apply(
			@AuthenticationPrincipal LoginUser user,
			@PathVariable long challengeId,
			@Valid @RequestBody(required = false) ApplyRequest request) {
		ApplyRequest body = request == null ? new ApplyRequest(null, null) : request;
		long participationId = applyService.apply(user.userId().value(), challengeId, body.message(), body.inviteCode());
		return ResponseEntity.status(HttpStatus.CREATED)
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(new AppliedResponse(participationId)));
	}

	@PostMapping("/{participationId}/approve")
	@Operation(summary = "참가 신청 승인",
			description = "방장이 승인 대기 신청을 승인합니다. 신청자에게 승인 알림이 갑니다. 정원이 찼으면 409입니다.")
	public ApiResponse<Map<String, Object>> approve(
			@AuthenticationPrincipal LoginUser user, @PathVariable long challengeId, @PathVariable long participationId) {
		decideService.approve(user.userId().value(), challengeId, participationId);
		return ApiResponse.successEmpty();
	}

	@PostMapping("/{participationId}/confirm-invalid")
	@Operation(summary = "무효 신청 확인",
			description = "동시 참여 한도 때문에 무효가 된 신청을 방장이 확인합니다(무효 알림의 \"확인\" 버튼). "
					+ "확인하면 승인 대기 목록에서 사라집니다. 이미 확인한 신청이면 그대로 성공하고, 무효가 아닌 신청은 409입니다.")
	public ApiResponse<Map<String, Object>> confirmInvalid(
			@AuthenticationPrincipal LoginUser user, @PathVariable long challengeId, @PathVariable long participationId) {
		decideService.confirmInvalidation(user.userId().value(), challengeId, participationId);
		return ApiResponse.successEmpty();
	}

	@PostMapping("/{participationId}/reject")
	@Operation(summary = "참가 신청 거절",
			description = "방장이 승인 대기 신청을 거절합니다. 걸어 둔 포인트는 전액 돌려주고, 신청자에게 거절 알림이 갑니다. "
					+ "거절 사유는 제공하지 않습니다.")
	public ApiResponse<Map<String, Object>> reject(
			@AuthenticationPrincipal LoginUser user, @PathVariable long challengeId, @PathVariable long participationId) {
		decideService.reject(user.userId().value(), challengeId, participationId);
		return ApiResponse.successEmpty();
	}
}

package com.morak.feature.certification.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.certification.application.dto.result.JudgedCertification;
import com.morak.feature.certification.application.dto.result.PendingJudgment;
import com.morak.feature.certification.application.service.JudgeCertificationService;
import com.morak.feature.certification.presentation.dto.request.RejectCertificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 방장·부방장 인증 판정(위키 §4.4). TODO(D-42): 경로·응답 필드는 프론트와 확정 전 제안값이다. */
@RestController
@RequestMapping("/challenges/{challengeId}/certifications")
@Tag(name = "챌린지 인증", description = "챌린지 인증 제출·피드 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CertificationJudgeController {

	private final JudgeCertificationService service;

	public CertificationJudgeController(JudgeCertificationService service) {
		this.service = service;
	}

	@GetMapping("/pending-judgments")
	@Operation(summary = "판정 대기 목록",
			description = "포인트 없는 챌린지에서 내가 판정할 인증(판정 대기, 마감 전)을 제출 순으로 돌려줍니다. 방장은 자신을 뺀 모든 팀원의 "
					+ "인증, 부방장은 방장의 인증만 옵니다. 일반 참여자와 포인트 챌린지(운영진 검수)는 빈 목록입니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
				description = "참여 중이 아님(CERTIFICATION_NOT_JUDGE)"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "챌린지 없음")
	})
	public ResponseEntity<ApiResponse<List<PendingJudgment>>> pending(@AuthenticationPrincipal LoginUser user,
			@PathVariable long challengeId) {
		return noStore(service.pending(user.userId().value(), challengeId));
	}

	@PostMapping("/{certificationId}/approve")
	@Operation(summary = "인증 승인", description = "판정 담당인 인증을 승인합니다. 판정 마감(인증 다음 날 12:00) 전에만 가능합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "승인 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
				description = "판정 담당이 아님·본인 인증·포인트 챌린지(CERTIFICATION_NOT_JUDGE)"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
				description = "챌린지 또는 인증 없음(CERTIFICATION_JUDGMENT_NOT_FOUND)"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
				description = "이미 판정됨(CERTIFICATION_ALREADY_JUDGED) 또는 판정 마감 지남(CERTIFICATION_JUDGE_DUE_PASSED)")
	})
	public ResponseEntity<ApiResponse<JudgedCertification>> approve(@AuthenticationPrincipal LoginUser user,
			@PathVariable long challengeId, @PathVariable long certificationId) {
		return noStore(service.approve(user.userId().value(), challengeId, certificationId));
	}

	@PostMapping("/{certificationId}/reject")
	@Operation(summary = "인증 거절",
			description = "판정 담당인 인증을 거절합니다. 사유는 필수(200자 이하)입니다. 제출자에게 인증 거절 알림이 갑니다. "
					+ "오류는 승인과 같고, 사유 오류는 400입니다.")
	public ResponseEntity<ApiResponse<JudgedCertification>> reject(@AuthenticationPrincipal LoginUser user,
			@PathVariable long challengeId, @PathVariable long certificationId,
			@RequestBody RejectCertificationRequest request) {
		return noStore(service.reject(user.userId().value(), challengeId, certificationId, request.reason()));
	}

	private static <T> ResponseEntity<ApiResponse<T>> noStore(T data) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
	}
}

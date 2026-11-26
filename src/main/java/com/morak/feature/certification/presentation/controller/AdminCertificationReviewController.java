package com.morak.feature.certification.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.certification.application.dto.result.CertificationReviewView.ChallengeSummary;
import com.morak.feature.certification.application.dto.result.CertificationReviewView.Detail;
import com.morak.feature.certification.application.dto.result.CertificationReviewView.Page;
import com.morak.feature.certification.application.service.CertificationReviewService;
import com.morak.feature.certification.application.service.CertificationReviewService.StatusFilter;
import com.morak.feature.certification.presentation.dto.request.RejectCertificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영진 인증 검수(위키 §4.10). {@code /admin/**}은 SecurityConfig에서 ADMIN만 허용한다. */
@RestController
@RequestMapping("/admin/certification-reviews")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminCertificationReviewController {

	private final CertificationReviewService service;

	public AdminCertificationReviewController(CertificationReviewService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "검수 목록",
			description = "포인트 공개 챌린지에 제출된 인증을 제출 시각 순으로 돌려줍니다. 최대 100건이며 totalCount는 조건에 맞는 전체 건수, "
					+ "pendingCount는 조건과 무관한 전체 미검수 건수, overdueCount는 그중 검수 마감(인증 다음 날 12:00 KST)이 지난 건수입니다. "
					+ "각 항목의 reviewDueAt이 그 인증의 검수 마감입니다. status 기본값은 PENDING(미검수만 보기)입니다.")
	public ResponseEntity<ApiResponse<Page>> list(
			@Parameter(description = "인증 날짜(KST). 없으면 전체") @RequestParam(required = false)
					@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate certDate,
			@Parameter(description = "챌린지 ID. 없으면 전체") @RequestParam(required = false) Long challengeId,
			@Parameter(description = "PENDING(미검수) / APPROVED / REJECTED / ALL") @RequestParam(defaultValue = "PENDING")
					StatusFilter status) {
		return noStore(service.list(certDate, challengeId, status));
	}

	@GetMapping("/challenges")
	@Operation(summary = "챌린지별 검수 현황",
			description = "검수 대상(포인트 공개) 챌린지마다 미검수·승인·거절 건수와 마감이 지난 미검수 건수를 돌려줍니다. 목록 필터의 챌린지 선택지로도 씁니다.")
	public ResponseEntity<ApiResponse<List<ChallengeSummary>>> challenges() {
		return noStore(service.challengeSummaries());
	}

	@GetMapping("/{certificationId}")
	@Operation(summary = "검수 상세", description = "원본 사진, 챌린지 공통 미션·인증 규칙과 처리 결과(처리된 경우)를 돌려줍니다. 검수 대상이 아니면 404입니다.")
	public ResponseEntity<ApiResponse<Detail>> detail(@PathVariable long certificationId) {
		return noStore(service.detail(certificationId));
	}

	@PostMapping("/{certificationId}/approve")
	@Operation(summary = "인증 승인",
			description = "미검수 인증을 승인합니다. 다른 운영진이 먼저 처리했으면 409(CERTIFICATION_ALREADY_JUDGED)이고 기존 판정은 바뀌지 않습니다.")
	public ResponseEntity<ApiResponse<Detail>> approve(
			@AuthenticationPrincipal LoginUser user, @PathVariable long certificationId) {
		return noStore(service.approve(user.userId().value(), certificationId));
	}

	@PostMapping("/{certificationId}/reject")
	@Operation(summary = "인증 거절",
			description = "미검수 인증을 거절하고 사유를 남깁니다. 사유는 필수(200자 이하)이고, 제출자에게 거절 알림(#12)이 갑니다. "
					+ "다른 운영진이 먼저 처리했으면 409(CERTIFICATION_ALREADY_JUDGED)입니다.")
	public ResponseEntity<ApiResponse<Detail>> reject(
			@AuthenticationPrincipal LoginUser user,
			@PathVariable long certificationId,
			@RequestBody RejectCertificationRequest request) {
		return noStore(service.reject(user.userId().value(), certificationId, request.reason()));
	}

	private static <T> ResponseEntity<ApiResponse<T>> noStore(T data) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
	}
}

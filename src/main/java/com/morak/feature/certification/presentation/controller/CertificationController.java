package com.morak.feature.certification.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.exception.BusinessException;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.certification.application.dto.result.CertificationFeed;
import com.morak.feature.certification.application.dto.result.SubmittedCertification;
import com.morak.feature.certification.application.service.ReadCertificationFeedService;
import com.morak.feature.certification.application.service.SubmitCertificationService;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 인증 제출·피드(위키 §4.4). TODO(D-34, D-35): 경로·응답 필드는 프론트와 확정 전 제안값이다. */
@RestController
@Tag(name = "챌린지 인증", description = "챌린지 인증 제출·피드 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CertificationController {

	private final SubmitCertificationService service;
	private final ReadCertificationFeedService feedService;

	public CertificationController(SubmitCertificationService service, ReadCertificationFeedService feedService) {
		this.service = service;
		this.feedService = feedService;
	}

	@GetMapping("/challenges/{challengeId}/certifications")
	@Operation(summary = "인증 피드",
			description = "한 날짜(KST)의 팀 인증을 제출 순으로 돌려줍니다. 다른 팀원의 인증은 승인된 것(APPROVED·AUTO_APPROVED)만, "
					+ "본인 인증은 판정 대기·거절도 status와 함께 옵니다. 참여 중이거나 끝까지 참여한 사람만 볼 수 있습니다. "
					+ "imageUrl은 300초 동안 유효한 서명 주소입니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "날짜 형식 오류"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
				description = "참여 확정자가 아님(CERTIFICATION_FEED_NOT_MEMBER). 승인 대기·퇴장 포함"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "챌린지 없음")
	})
	public ResponseEntity<ApiResponse<CertificationFeed>> feed(@AuthenticationPrincipal LoginUser user,
			@PathVariable long challengeId,
			@Parameter(description = "인증 날짜(KST, yyyy-MM-dd). 없으면 오늘") @RequestParam(required = false)
					@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(feedService.read(user.userId().value(), challengeId, date)));
	}

	@PostMapping(path = "/challenges/{challengeId}/certifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "인증 제출",
			description = "오늘(KST) 인증을 사진 한 장(JPEG·PNG·WebP, 5MB 이하)으로 제출합니다. 하루 1회이고 제출 후 수정·삭제·재제출은 "
					+ "없습니다. 진행 중인 챌린지에서 참여 중(ACTIVE)일 때만 가능합니다. 상태는 PENDING(판정 대기)으로 시작합니다. "
					+ "certDate에는 인증하기를 누른 날짜를 보냅니다. 서버가 요청을 다 받은 시각의 날짜와 다르면(자정을 넘겨 도착) 거절합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "제출 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
				description = "참여 중이 아님(CERTIFICATION_NOT_ACTIVE_PARTICIPANT)"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
				description = "진행 중이 아님(CERTIFICATION_CHALLENGE_NOT_IN_PROGRESS), 오늘 이미 인증함(CERTIFICATION_ALREADY_SUBMITTED) "
						+ "또는 마감 지남·날짜 불일치(CERTIFICATION_DATE_MISMATCH)"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "사진 용량 초과")
	})
	public ResponseEntity<ApiResponse<SubmittedCertification>> submit(@AuthenticationPrincipal LoginUser user,
			@PathVariable long challengeId,
			@Parameter(description = "인증하려는 날짜(KST, yyyy-MM-dd). 인증하기를 누른 날짜") @RequestParam
					@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate certDate,
			@RequestPart("image") List<MultipartFile> images) {
		if (images.size() != 1) {
			throw new BusinessException(CertificationErrorCode.INVALID_IMAGE);
		}
		MultipartFile image = images.getFirst();
		if (image.getSize() > SubmitCertificationService.MAX_IMAGE_BYTES) {
			throw new BusinessException(CertificationErrorCode.IMAGE_TOO_LARGE);
		}
		try {
			return ResponseEntity.status(HttpStatus.CREATED)
					.cacheControl(CacheControl.noStore())
					.body(ApiResponse.success(service.submit(user.userId().value(), challengeId, certDate, image.getBytes())));
		} catch (IOException exception) {
			throw new BusinessException(CertificationErrorCode.INVALID_IMAGE);
		}
	}
}

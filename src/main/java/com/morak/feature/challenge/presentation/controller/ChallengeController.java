package com.morak.feature.challenge.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.challenge.application.service.CreateChallengeService;
import com.morak.feature.challenge.application.service.CreateChallengeService.CreatedChallenge;
import com.morak.feature.challenge.presentation.dto.request.CreateChallengeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/challenges")
public class ChallengeController {

	private final CreateChallengeService service;

	public ChallengeController(CreateChallengeService service) {
		this.service = service;
	}

	@PostMapping
	@Operation(
			summary = "챌린지 개설",
			description = "온보딩을 완료한 사용자가 챌린지를 개설하고 방장으로 등록됩니다.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = CreateChallengeRequest.class),
							examples = @ExampleObject(
									name = "비공개 챌린지",
									description = "예시 시작일이 지났다면 KST 기준 내일 이후로 두 날짜를 변경하세요.",
									value = """
											{
											"title": "아침 독서",
											"visibility": "PRIVATE",
											"category": "READING",
											"purpose": "매일 읽는 습관 만들기",
											"commonMission": "매일 10쪽 읽기",
											"verificationRules": "읽은 쪽 사진 올리기",
											"startDate": "2027-01-04",
											"endDate": "2027-01-10",
											"capacity": 2
											}
											"""))))
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "201", description = "개설 성공. 챌린지 ID와 초대 코드를 반환합니다.",
				useReturnTypeSchema = true),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "400", description = "필수 항목 누락 또는 날짜, 정원 등 요청 값이 올바르지 않습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "401", description = "로그인이 필요하거나 액세스 토큰이 유효하지 않습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "403", description = "온보딩을 완료해야 합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "409", description = "동시 참여 한도에 도달했거나 개설에 포인트 처리가 필요합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "503", description = "초대 코드를 발급하지 못했습니다.")
	})
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public ResponseEntity<ApiResponse<CreatedChallenge>> create(@AuthenticationPrincipal LoginUser user,
			@Valid @RequestBody CreateChallengeRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(service.create(user.userId().value(), request.toCommand())));
	}
}

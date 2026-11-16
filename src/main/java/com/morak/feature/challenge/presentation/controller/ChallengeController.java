package com.morak.feature.challenge.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.challenge.application.service.ChallengeCoverImages;
import com.morak.feature.challenge.application.service.CreateChallengeService;
import com.morak.feature.challenge.application.service.CreateChallengeService.CreatedChallenge;
import com.morak.feature.challenge.application.service.ReadChallengeService;
import com.morak.feature.challenge.application.service.ReadChallengeService.CategoryItem;
import com.morak.feature.challenge.application.service.ReadChallengeService.ChallengePage;
import com.morak.feature.challenge.application.service.ReadChallengeService.ChallengeView;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.presentation.dto.request.CreateChallengeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/challenges")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ChallengeController {

	private final CreateChallengeService service;
	private final ReadChallengeService readService;
	private final ChallengeCoverImages coverImages;

	public ChallengeController(CreateChallengeService service, ReadChallengeService readService, ChallengeCoverImages coverImages) {
		this.service = service;
		this.readService = readService;
		this.coverImages = coverImages;
	}

	@GetMapping
	@Operation(summary = "공개 챌린지 목록", description = "모집 중인 공개 챌린지를 기본 20개, 최대 100개까지 조회합니다.")
	public ApiResponse<ChallengePage> list(
			@RequestParam(required = false) ChallengeCategory category,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return ApiResponse.success(readService.list(category, keyword, cursor, size));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidParameter() {
		var error = CommonErrorCode.INVALID_REQUEST;
		return ResponseEntity.status(error.status()).body(ApiResponse.failure(error.code(), error.message()));
	}

	@GetMapping("/categories")
	@Operation(summary = "챌린지 카테고리")
	public ApiResponse<java.util.List<CategoryItem>> categories() {
		return ApiResponse.success(readService.categories());
	}

	@GetMapping("/{id}")
	@Operation(summary = "챌린지 상세", description = "비공개 챌린지는 방장, 참가자 또는 신청자만 조회할 수 있습니다.")
	public ResponseEntity<ApiResponse<ChallengeView>> get(@PathVariable long id,
			@AuthenticationPrincipal LoginUser user) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(readService.get(id, user == null ? null : user.userId().value())));
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
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
											"description": "아침마다 함께 책 읽어요",
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
				responseCode = "503", description = "참가 연동 또는 필요한 무효화 알림 연동이 준비 중이거나 초대 코드 발급에 실패했습니다.")
	})
	public ResponseEntity<ApiResponse<CreatedChallenge>> create(@AuthenticationPrincipal LoginUser user,
			@Valid @RequestBody CreateChallengeRequest request) {
		return created(service.create(user.userId().value(), request.toCommand()));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "대표 사진과 함께 챌린지 개설",
			description = "challenge는 JSON, coverImage는 5MB 이하 사진 한 장입니다.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
							encoding = @Encoding(name = "challenge", contentType = MediaType.APPLICATION_JSON_VALUE))))
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "개설 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "대표 사진 용량 초과")
	})
	public ResponseEntity<ApiResponse<CreatedChallenge>> createWithCover(@AuthenticationPrincipal LoginUser user,
			@Valid @RequestPart("challenge") CreateChallengeRequest request,
			@RequestPart("coverImage") List<MultipartFile> photos) {
		if (photos.size() != 1) {
			throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
		}
		MultipartFile photo = photos.getFirst();
		if (photo.getSize() > ChallengeCoverImages.MAX_IMAGE_BYTES) {
			throw new BusinessException(ChallengeErrorCode.COVER_IMAGE_TOO_LARGE);
		}
		try {
			return created(coverImages.create(user.userId().value(), request.toCommand(), photo.getBytes()));
		} catch (IOException exception) {
			throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
		}
	}

	private ResponseEntity<ApiResponse<CreatedChallenge>> created(CreatedChallenge challenge) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(challenge));
	}
}

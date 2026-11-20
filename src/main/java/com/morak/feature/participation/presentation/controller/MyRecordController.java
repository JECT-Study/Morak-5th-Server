package com.morak.feature.participation.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.participation.application.dto.result.MyRecordView;
import com.morak.feature.participation.application.service.ReadMyRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 기록(프로필 통계). */
@RestController
@RequestMapping("/users/me/records")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MyRecordController {

	private final ReadMyRecordService service;

	public MyRecordController(ReadMyRecordService service) {
		this.service = service;
	}

	// TODO(D-28): 응답 필드명은 프론트와 확정 전 제안값이다.
	@GetMapping
	@Operation(summary = "내 기록",
			description = "개인 인증률(진행 중인 챌린지 포함 누적), 모락이 단계, 누적 완주 횟수, 중도 실패 횟수를 돌려줍니다. "
					+ "인증 기록이 없으면 인증률·모락이 단계는 null이고 hasCertificationRecord는 false입니다.")
	public ResponseEntity<ApiResponse<MyRecordView>> read(@AuthenticationPrincipal LoginUser user) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(service.read(user.userId().value())));
	}
}

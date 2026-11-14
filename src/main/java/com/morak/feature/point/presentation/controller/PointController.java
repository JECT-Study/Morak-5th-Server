package com.morak.feature.point.presentation.controller;

import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.point.application.service.PointService;
import com.morak.feature.point.presentation.dto.response.PointLedgerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "포인트", description = "포인트 잔액·거래 내역 API")
public class PointController {

	private final PointService pointService;

	public PointController(PointService pointService) {
		this.pointService = pointService;
	}

	@GetMapping("/points/me")
	@Operation(summary = "내 포인트 조회",
			description = "현재 잔액과 거래 내역을 최신순으로 반환합니다. 다음 페이지는 응답의 nextCursor를 before로 넘겨 조회합니다. "
					+ "잔액과 내역은 같은 시점 기준입니다.")
	public ResponseEntity<ApiResponse<PointLedgerResponse>> getMyLedger(
			@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "이 id보다 오래된 거래") @RequestParam(required = false) Long before,
			@Parameter(description = "조회 개수 (기본 20, 최대 100)") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponse.success(
				PointLedgerResponse.from(pointService.getLedger(loginUser.userId().value(), before, limit))));
	}
}

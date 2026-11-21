package com.morak.feature.participation.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Mine;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Team;
import com.morak.feature.participation.application.service.ReadChallengeResultsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 챌린지 종료 후 결과 조회. 참가자만 볼 수 있다(위키 §4.5). */
@RestController
@RequestMapping("/challenges/{challengeId}/results")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ChallengeResultController {

	private final ReadChallengeResultsService service;

	public ChallengeResultController(ReadChallengeResultsService service) {
		this.service = service;
	}

	// TODO(D-28): 응답 필드와 오류 코드(참가자 아님 403, 결과 확정 전 409)는 프론트와 확정 전 제안값이다.
	@GetMapping
	@Operation(summary = "팀 결과",
			description = "챌린지 종료 후 팀 달성률(분자·분모)과 팀원별 성공·실패 횟수, 퇴장자 표시를 돌려줍니다. 참가자만 볼 수 있고, "
					+ "마지막 날 인증까지 판정되어 결과가 확정되기 전이면 409입니다.")
	public ResponseEntity<ApiResponse<Team>> team(@AuthenticationPrincipal LoginUser user, @PathVariable long challengeId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(service.team(user.userId().value(), challengeId)));
	}

	@GetMapping("/me")
	@Operation(summary = "내 결과",
			description = "챌린지 종료 후 내 성공률, 팀 달성률, 완주 여부, 퇴장 여부를 돌려줍니다. 정산 결과(반환·차감 포인트)와 "
					+ "이번에 받은 메달은 정산 구현 뒤에 추가됩니다. 결과가 확정되기 전이면 409입니다.")
	public ResponseEntity<ApiResponse<Mine>> mine(@AuthenticationPrincipal LoginUser user, @PathVariable long challengeId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(service.mine(user.userId().value(), challengeId)));
	}
}

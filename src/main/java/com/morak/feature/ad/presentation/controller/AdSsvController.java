package com.morak.feature.ad.presentation.controller;

import com.morak.common.response.ApiResponse;
import com.morak.feature.ad.application.service.ClaimAdRewardBySsvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AdMob이 광고 시청 완료 후 서버로 직접 호출하는 알림(SSV). 로그인 토큰이 없어 SecurityConfig에서 열어 두고,
 * 구글 서명 확인이 인증을 대신한다. 앱·웹 클라이언트는 이 API를 호출하지 않는다.
 */
@RestController
@RequestMapping("/ads")
@Tag(name = "광고", description = "광고 시청 보상 API")
public class AdSsvController {

	private final ClaimAdRewardBySsvService service;

	public AdSsvController(ClaimAdRewardBySsvService service) {
		this.service = service;
	}

	@GetMapping("/ssv")
	@Operation(summary = "광고 시청 완료 알림(AdMob SSV, 구글 서버가 호출)",
			description = "AdMob 광고 단위의 서버 측 확인 콜백 URL로 등록하는 주소입니다. 구글 서명을 확인한 뒤 500P를 지급하고, "
					+ "같은 시청 토큰은 한 번만 지급합니다. 앱에서 직접 호출하지 않습니다.")
	public ApiResponse<Map<String, Object>> reward(HttpServletRequest request) {
		// 서명은 디코딩 전 원문에 대해 계산되므로 @RequestParam이 아니라 원본 쿼리 문자열을 넘긴다.
		service.claim(request.getQueryString());
		return ApiResponse.successEmpty();
	}
}

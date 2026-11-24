package com.morak.feature.certification.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 사유 검증(공백·200자)은 {@code RejectReason}이 하고 오류 코드로 돌려준다. */
public record RejectCertificationRequest(
		@Schema(description = "제출자에게 표시되는 거절 사유. 필수, 200자 이하", example = "인증 사진에서 운동 시간을 확인할 수 없어요.")
				String reason) {}

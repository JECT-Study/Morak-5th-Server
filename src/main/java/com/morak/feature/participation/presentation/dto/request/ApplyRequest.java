package com.morak.feature.participation.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ApplyRequest(
		@Schema(description = "지원서(각오 한마디). 200자 이하", example = "매일 꾸준히 참여할게요!") @Size(max = 200) String message,
		@Schema(description = "비공개 챌린지의 초대 코드. 공개 챌린지에서는 보내지 않습니다.", example = "123456") String inviteCode) {}

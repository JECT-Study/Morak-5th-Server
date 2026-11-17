package com.morak.feature.notification.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record UnreadCountResponse(
		@Schema(description = "읽지 않은 알림 개수 (종 아이콘에 표시)", example = "3")
		long unreadCount) {}

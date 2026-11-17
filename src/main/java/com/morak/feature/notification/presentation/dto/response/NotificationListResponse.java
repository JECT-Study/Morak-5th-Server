package com.morak.feature.notification.presentation.dto.response;

import com.morak.feature.notification.application.dto.NotificationListResult;
import com.morak.feature.notification.domain.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record NotificationListResponse(
		@Schema(description = "알림 목록 (최신순)")
		List<NotificationResponse> notifications,
		@Schema(description = "다음 페이지 요청 시 before로 넘길 값. 마지막 페이지면 null", nullable = true, example = "31")
		Long nextCursor) {

	public static NotificationListResponse from(NotificationListResult result) {
		return new NotificationListResponse(
				result.notifications().stream().map(NotificationResponse::from).toList(), result.nextCursor());
	}

	public record NotificationResponse(
			@Schema(description = "알림 ID", example = "42")
			Long id,
			@Schema(description = "알림 종류")
			NotificationType type,
			@Schema(description = "알림 제목", example = "참여 확정")
			String title,
			@Schema(description = "알림 본문", example = "매일 아침 러닝 참여가 확정됐어요")
			String body,
			@Schema(description = "알림을 눌렀을 때 이동할 대상 종류. 이동할 곳이 없으면 null", nullable = true, example = "CHALLENGE")
			String targetType,
			@Schema(description = "이동할 대상 ID. 이동할 곳이 없으면 null", nullable = true, example = "5")
			Long targetId,
			@Schema(description = "읽음 여부", example = "false")
			boolean read,
			@Schema(description = "알림 생성 시각 (UTC, 화면에는 KST로 변환)", example = "2026-11-17T03:00:00Z")
			Instant createdAt) {

		static NotificationResponse from(NotificationListResult.Entry entry) {
			return new NotificationResponse(
					entry.id(),
					entry.type(),
					entry.title(),
					entry.body(),
					entry.targetType(),
					entry.targetId(),
					entry.read(),
					entry.createdAt());
		}
	}
}

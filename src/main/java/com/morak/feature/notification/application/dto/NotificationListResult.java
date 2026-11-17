package com.morak.feature.notification.application.dto;

import com.morak.feature.notification.domain.entity.Notification;
import com.morak.feature.notification.domain.entity.NotificationType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * @param notifications 최신순
 * @param nextCursor 다음 페이지 요청 시 before로 넘길 값. 마지막 페이지면 null
 */
public record NotificationListResult(List<Entry> notifications, Long nextCursor) {

	public static NotificationListResult of(List<Notification> notifications, Long nextCursor) {
		return new NotificationListResult(notifications.stream().map(Entry::from).toList(), nextCursor);
	}

	/**
	 * @param targetType 알림을 눌렀을 때 이동할 대상 종류. 이동할 곳이 없으면 null
	 * @param targetId 이동할 대상 ID. 이동할 곳이 없으면 null
	 * @param read 읽음 여부
	 */
	public record Entry(
			Long id,
			NotificationType type,
			String title,
			String body,
			String targetType,
			Long targetId,
			boolean read,
			Instant createdAt) {

		static Entry from(Notification notification) {
			return new Entry(
					notification.getId(),
					notification.getType(),
					notification.getTitle(),
					notification.getBody(),
					notification.getTargetType(),
					notification.getTargetId(),
					notification.isRead(),
					notification.getCreatedAt().toInstant(ZoneOffset.UTC));
		}
	}
}

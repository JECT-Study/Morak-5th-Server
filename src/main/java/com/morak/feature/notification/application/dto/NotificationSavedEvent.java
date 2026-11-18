package com.morak.feature.notification.application.dto;

import com.morak.feature.notification.domain.entity.NotificationType;

/**
 * 알림이 새로 저장됐다는 사실. 알림을 저장한 트랜잭션이 커밋된 뒤에만 푸시 발송이 이 이벤트를 받는다.
 * 롤백되면 이벤트도 사라져 푸시가 나가지 않는다.
 */
public record NotificationSavedEvent(
		NotificationType type,
		Long recipientUserId,
		String title,
		String body,
		String targetType,
		Long targetId) {}

package com.morak.feature.notification.application.dto;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.domain.entity.Notification;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;

/**
 * 다른 도메인이 알림을 만들 때 notify()에 넘기는 요청서.
 *
 * @param recipientUserId 알림을 받을 사용자 ID
 * @param content 알림 종류와 문구에 들어갈 값. 문구는 알림 도메인이 정한다.
 * @param targetType 알림을 눌렀을 때 이동할 대상 종류. 이동할 곳이 없으면 targetId와 함께 null
 * @param targetId 이동할 대상 ID
 * @param dedupKey 같은 알림을 두 번 만들지 않기 위한 키. 전역 유니크이므로 수신자가 여럿인 알림은 수신자 ID를
 *     포함해야 한다. 중복 방지가 필요 없는 알림은 null
 */
public record NotifyCommand(
		Long recipientUserId, NotificationContent content, String targetType, Long targetId, String dedupKey) {

	public NotifyCommand {
		if (recipientUserId == null || recipientUserId <= 0) {
			throw invalid("recipientUserId=" + recipientUserId);
		}
		if (content == null) {
			throw invalid("content is null");
		}
		if ((targetType == null) != (targetId == null)) {
			throw invalid("targetType=" + targetType + ", targetId=" + targetId);
		}
		if (targetType != null && (targetType.isBlank() || targetType.length() > Notification.MAX_TARGET_TYPE_LENGTH)) {
			throw invalid("targetType length=" + targetType.length());
		}
		if (dedupKey != null && (dedupKey.isBlank() || dedupKey.length() > Notification.MAX_DEDUP_KEY_LENGTH)) {
			throw invalid("dedupKey length=" + dedupKey.length());
		}
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(NotificationErrorCode.INVALID_COMMAND, detail);
	}
}

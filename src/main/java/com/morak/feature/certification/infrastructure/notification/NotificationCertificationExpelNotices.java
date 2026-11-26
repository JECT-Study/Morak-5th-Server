package com.morak.feature.certification.infrastructure.notification;

import com.morak.feature.certification.application.port.out.CertificationExpelNotices;
import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import org.springframework.stereotype.Component;

/** 퇴장·조기 종료 알림을 알림 도메인으로 보낸다. */
@Component
class NotificationCertificationExpelNotices implements CertificationExpelNotices {

	private static final String TARGET_CHALLENGE = "CHALLENGE";

	private final NotificationService notifications;

	NotificationCertificationExpelNotices(NotificationService notifications) {
		this.notifications = notifications;
	}

	@Override
	public void expelled(long recipientUserId, long challengeId, long participationId, String challengeTitle,
			int failCount, int pointAmount) {
		// 퇴장은 참가마다 한 번이라 참가 ID로 막는다.
		notifications.notify(new NotifyCommand(recipientUserId,
				new NotificationContent.Expelled(challengeTitle, failCount, pointOrNull(pointAmount)),
				TARGET_CHALLENGE, challengeId, "EXPELLED:" + participationId));
	}

	@Override
	public void earlyEnded(long recipientUserId, long challengeId, String challengeTitle, int pointAmount) {
		notifications.notify(new NotifyCommand(recipientUserId,
				new NotificationContent.EarlyEnded(challengeTitle, pointOrNull(pointAmount)),
				TARGET_CHALLENGE, challengeId, "EARLY_ENDED:" + challengeId + ":" + recipientUserId));
	}

	/** 알림 본문은 포인트가 없으면 null을 받는다. */
	private static Long pointOrNull(int pointAmount) {
		return pointAmount > 0 ? (long) pointAmount : null;
	}
}

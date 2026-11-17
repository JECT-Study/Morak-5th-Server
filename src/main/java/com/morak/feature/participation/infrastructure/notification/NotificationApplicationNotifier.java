package com.morak.feature.participation.infrastructure.notification;

import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import com.morak.feature.participation.application.port.out.ApplicationNotifier;
import org.springframework.stereotype.Component;

@Component
class NotificationApplicationNotifier implements ApplicationNotifier {

	private static final String TARGET_CHALLENGE = "CHALLENGE";

	private final NotificationService notifications;

	NotificationApplicationNotifier(NotificationService notifications) {
		this.notifications = notifications;
	}

	@Override
	public void approved(long applicantUserId, long challengeId, long participationId, String challengeTitle) {
		notifications.notify(new NotifyCommand(
				applicantUserId,
				new NotificationContent.ParticipationApproved(challengeTitle),
				TARGET_CHALLENGE,
				challengeId,
				"PARTICIPATION_APPROVED:" + participationId));
	}

	@Override
	public void rejected(
			long applicantUserId, long challengeId, long participationId, String challengeTitle, int pointAmount) {
		notifications.notify(new NotifyCommand(
				applicantUserId,
				new NotificationContent.ParticipationRejected(challengeTitle, (long) pointAmount),
				TARGET_CHALLENGE,
				challengeId,
				"PARTICIPATION_REJECTED:" + participationId));
	}
}

package com.morak.feature.challenge.infrastructure.notification;

import com.morak.feature.challenge.application.port.out.ChallengeDiscardNotice;
import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import org.springframework.stereotype.Component;

@Component
class NotificationChallengeDiscardNotice implements ChallengeDiscardNotice {

	private static final String TARGET_CHALLENGE = "CHALLENGE";

	private final NotificationService notifications;

	NotificationChallengeDiscardNotice(NotificationService notifications) {
		this.notifications = notifications;
	}

	@Override
	public void discardedByHost(
			long recipientUserId, long challengeId, long participationId, String challengeTitle, int refundedPoint) {
		notifications.notify(new NotifyCommand(
				recipientUserId,
				new NotificationContent.ChallengeDiscarded(challengeTitle, (long) refundedPoint),
				TARGET_CHALLENGE,
				challengeId,
				"CHALLENGE_DISCARDED:" + participationId));
	}
}

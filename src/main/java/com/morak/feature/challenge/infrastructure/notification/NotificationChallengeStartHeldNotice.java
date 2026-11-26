package com.morak.feature.challenge.infrastructure.notification;

import com.morak.feature.challenge.application.port.out.ChallengeStartHeldNotice;
import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
class NotificationChallengeStartHeldNotice implements ChallengeStartHeldNotice {

	private static final String TARGET_CHALLENGE = "CHALLENGE";

	private final NotificationService notifications;

	NotificationChallengeStartHeldNotice(NotificationService notifications) {
		this.notifications = notifications;
	}

	@Override
	public void startHeld(
			long recipientUserId, long challengeId, String challengeTitle, LocalDate deadlineDate, LocalDate startDate) {
		// 같은 날 배치를 다시 돌려도 한 번만 남고, 재설정 후 새 시작일에 다시 보류되면 새로 남는다. TODO(D-29)
		notifications.notify(new NotifyCommand(
				recipientUserId,
				new NotificationContent.StartHeld(challengeTitle, deadlineDate),
				TARGET_CHALLENGE,
				challengeId,
				"START_HELD:" + challengeId + ":" + startDate));
	}
}

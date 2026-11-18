package com.morak.feature.challenge.infrastructure.notification;

import com.morak.feature.challenge.application.port.out.ChallengeScheduleResetNotice;
import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
class NotificationChallengeScheduleResetNotice implements ChallengeScheduleResetNotice {

	private static final String TARGET_CHALLENGE = "CHALLENGE";

	private final NotificationService notifications;

	NotificationChallengeScheduleResetNotice(NotificationService notifications) {
		this.notifications = notifications;
	}

	@Override
	public void scheduleReset(long recipientUserId, long challengeId, long participationId, String challengeTitle,
			LocalDate startDate, LocalDate endDate) {
		// 재설정할 때마다 시작일이 앞으로만 가므로(새 시작일은 내일 이후) 시작일만으로 키가 유일하다.
		notifications.notify(new NotifyCommand(
				recipientUserId,
				new NotificationContent.ScheduleReset(challengeTitle, startDate, endDate),
				TARGET_CHALLENGE,
				challengeId,
				"SCHEDULE_RESET:" + participationId + ":" + startDate));
	}
}

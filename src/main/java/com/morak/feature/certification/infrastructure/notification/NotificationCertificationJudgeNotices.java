package com.morak.feature.certification.infrastructure.notification;

import com.morak.feature.certification.application.port.out.CertificationJudgeNotices;
import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** 판정 알림을 알림 도메인으로 보낸다. */
@Component
class NotificationCertificationJudgeNotices implements CertificationJudgeNotices {

	private static final String TARGET_CHALLENGE = "CHALLENGE";

	private final NotificationService notifications;

	NotificationCertificationJudgeNotices(NotificationService notifications) {
		this.notifications = notifications;
	}

	@Override
	public void rejected(long recipientUserId, long challengeId, long certificationId, String challengeTitle, LocalDate certDate,
			int remainingFailCount) {
		// 인증 한 건은 한 번만 거절되지만, 재시도에도 알림이 하나만 남도록 인증 ID로 막는다.
		notifications.notify(new NotifyCommand(recipientUserId,
				new NotificationContent.CertificationRejected(challengeTitle, certDate, remainingFailCount),
				TARGET_CHALLENGE, challengeId, "CERTIFICATION_REJECTED:" + certificationId));
	}
}

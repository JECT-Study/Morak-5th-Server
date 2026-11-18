package com.morak.feature.challenge.infrastructure.notification;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.port.out.InvalidatedApplicationNotice;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 동시 참여 한도로 신청이 무효가 되면 그 신청을 받은 방장에게 알림 #3을 남긴다.
 * 개설 트랜잭션에 참여하므로 개설이 롤백되면 알림도 사라진다. 알림은 신청마다 한 번만 저장된다.
 */
@Component
class NotificationInvalidatedApplicationNotice implements InvalidatedApplicationNotice {

	private static final String TARGET_CHALLENGE = "CHALLENGE";

	private final NotificationService notifications;
	private final ChallengeRepository challenges;

	NotificationInvalidatedApplicationNotice(NotificationService notifications, ChallengeRepository challenges) {
		this.notifications = notifications;
		this.challenges = challenges;
	}

	@Override
	public void send(long recipientId, long challengeId, long participationId, Instant now) {
		// 무효가 된 신청이 걸린 챌린지의 이름이다 (방금 개설한 챌린지가 아니다).
		String title = challenges.findById(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
				.getTitle();
		notifications.notify(new NotifyCommand(
				recipientId,
				new NotificationContent.ApplicationInvalidated(title),
				TARGET_CHALLENGE,
				challengeId,
				"APPLICATION_INVALIDATED:" + participationId));
	}
}

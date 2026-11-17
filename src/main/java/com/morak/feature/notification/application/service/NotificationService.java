package com.morak.feature.notification.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.domain.entity.Notification;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import com.morak.feature.notification.domain.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 부르는 알림 생성 창구. 문구를 조립해 저장하고, 같은 dedup_key는 한 번만 저장한다.
 * 호출한 도메인의 트랜잭션에 참여하므로, 호출한 쪽이 롤백되면 알림도 함께 사라진다.
 */
@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationRepository notificationRepository;
	private final Clock clock;

	public NotificationService(NotificationRepository notificationRepository, Clock clock) {
		this.notificationRepository = notificationRepository;
		this.clock = clock;
	}

	/**
	 * 알림을 저장한다. 같은 dedupKey의 알림이 이미 있으면 저장하지 않고 false를 돌려준다.
	 * 중복은 오류가 아니므로 예외를 던지지 않는다 (배치를 다시 돌려도 결과가 같아야 한다).
	 */
	@Transactional
	public boolean notify(NotifyCommand command) {
		NotificationContent content = command.content();
		String title = content.title();
		String body = content.body();
		if (title.length() > Notification.MAX_TITLE_LENGTH || body.length() > Notification.MAX_BODY_LENGTH) {
			throw new BusinessException(
					NotificationErrorCode.INVALID_COMMAND,
					"type=" + content.type() + ", titleLength=" + title.length() + ", bodyLength=" + body.length());
		}
		// PostgreSQL은 마이크로초까지만 저장하므로 미리 잘라 둔다.
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
		Notification notification = Notification.create(
				command.recipientUserId(),
				content.type(),
				title,
				body,
				command.targetType(),
				command.targetId(),
				command.dedupKey(),
				now);
		boolean created = notificationRepository.saveIfAbsent(notification);
		if (!created) {
			log.debug(
					"알림 중복으로 건너뜀 type={} recipientUserId={} dedupKey={}",
					content.type(),
					command.recipientUserId(),
					command.dedupKey());
		}
		return created;
	}
}

package com.morak.feature.notification.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.application.dto.NotificationListResult;
import com.morak.feature.notification.domain.entity.Notification;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import com.morak.feature.notification.domain.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림함 조회와 읽음 처리. 항상 로그인한 본인의 알림만 다룬다. */
@Service
public class NotificationInboxService {

	public static final int DEFAULT_LIMIT = 20;
	public static final int MAX_LIMIT = 100;

	private final NotificationRepository notificationRepository;
	private final Clock clock;

	public NotificationInboxService(NotificationRepository notificationRepository, Clock clock) {
		this.notificationRepository = notificationRepository;
		this.clock = clock;
	}

	/** 최신순 목록. limit은 1~100으로 맞추고, 없으면 20개다. */
	@Transactional(readOnly = true)
	public NotificationListResult getNotifications(Long userId, Long before, Integer limit) {
		if (before != null && before <= 0) {
			throw new BusinessException(NotificationErrorCode.INVALID_CURSOR, "userId=" + userId + " before=" + before);
		}
		int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
		// 한 개 더 읽어 다음 페이지가 있는지 판단한다.
		List<Notification> rows = notificationRepository.findByRecipient(userId, before, size + 1);
		boolean hasNext = rows.size() > size;
		List<Notification> page = hasNext ? rows.subList(0, size) : rows;
		Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
		return NotificationListResult.of(page, nextCursor);
	}

	/** 읽지 않은 알림 개수 (홈 우측 상단 종 아이콘). */
	@Transactional(readOnly = true)
	public long countUnread(Long userId) {
		return notificationRepository.countUnread(userId);
	}

	/**
	 * 알림 한 건을 읽음 처리한다. 이미 읽은 알림이면 아무것도 바꾸지 않고 성공한다.
	 * 없는 알림과 다른 사람의 알림은 같은 오류로 거절한다.
	 */
	@Transactional
	public void markRead(Long userId, Long notificationId) {
		if (!notificationRepository.existsByIdAndRecipient(notificationId, userId)) {
			throw new BusinessException(
					NotificationErrorCode.NOTIFICATION_NOT_FOUND,
					"userId=" + userId + " notificationId=" + notificationId);
		}
		notificationRepository.markRead(notificationId, userId, now());
	}

	/** 읽지 않은 알림을 모두 읽음 처리하고, 새로 읽음 처리한 개수를 돌려준다. */
	@Transactional
	public int markAllRead(Long userId) {
		return notificationRepository.markAllRead(userId, now());
	}

	private LocalDateTime now() {
		// PostgreSQL은 마이크로초까지만 저장하므로 미리 잘라 둔다.
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}

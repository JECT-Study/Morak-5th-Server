package com.morak.feature.notification.domain.repository;

import com.morak.feature.notification.domain.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository {

	/**
	 * 같은 dedup_key의 알림이 이미 있으면 저장하지 않고 false를 돌려준다. 중복이어도 예외를 던지지 않으므로
	 * 호출한 도메인의 트랜잭션이 롤백 전용으로 바뀌지 않는다. dedup_key가 null이면 항상 저장한다.
	 */
	boolean saveIfAbsent(Notification notification);

	/** 수신자의 알림을 id 내림차순(최신순)으로 limit개. beforeId가 있으면 그보다 작은 id만. */
	List<Notification> findByRecipient(Long recipientUserId, Long beforeId, int limit);

	long countUnread(Long recipientUserId);

	/** 수신자 본인의 알림인지 확인할 때 쓴다. */
	boolean existsByIdAndRecipient(Long id, Long recipientUserId);

	/** 아직 읽지 않은 본인 알림만 읽음 처리한다. 이미 읽은 알림은 그대로 두고, 바꾼 행 수(0 또는 1)를 돌려준다. */
	int markRead(Long id, Long recipientUserId, LocalDateTime readAtUtc);

	/** 수신자의 읽지 않은 알림을 모두 읽음 처리하고 바꾼 행 수를 돌려준다. */
	int markAllRead(Long recipientUserId, LocalDateTime readAtUtc);
}

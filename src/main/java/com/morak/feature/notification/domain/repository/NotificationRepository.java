package com.morak.feature.notification.domain.repository;

import com.morak.feature.notification.domain.entity.Notification;

public interface NotificationRepository {

	/**
	 * 같은 dedup_key의 알림이 이미 있으면 저장하지 않고 false를 돌려준다. 중복이어도 예외를 던지지 않으므로
	 * 호출한 도메인의 트랜잭션이 롤백 전용으로 바뀌지 않는다. dedup_key가 null이면 항상 저장한다.
	 */
	boolean saveIfAbsent(Notification notification);
}

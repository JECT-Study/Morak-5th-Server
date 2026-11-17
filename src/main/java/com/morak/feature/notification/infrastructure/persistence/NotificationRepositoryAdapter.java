package com.morak.feature.notification.infrastructure.persistence;

import com.morak.feature.notification.domain.entity.Notification;
import com.morak.feature.notification.domain.repository.NotificationRepository;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

	private final JpaNotificationRepository jpaRepository;

	public NotificationRepositoryAdapter(JpaNotificationRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public boolean saveIfAbsent(Notification n) {
		return jpaRepository.insertIgnoringDuplicate(
						n.getRecipientUserId(),
						n.getType().name(),
						n.getTitle(),
						n.getBody(),
						n.getTargetType(),
						n.getTargetId(),
						n.getDedupKey(),
						n.getCreatedAt())
				== 1;
	}
}

package com.morak.feature.notification.infrastructure.persistence;

import com.morak.feature.notification.domain.entity.Notification;
import com.morak.feature.notification.domain.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
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

	@Override
	public List<Notification> findByRecipient(Long recipientUserId, Long beforeId, int limit) {
		PageRequest page = PageRequest.of(0, limit);
		return beforeId == null
				? jpaRepository.findLatest(recipientUserId, page)
				: jpaRepository.findBefore(recipientUserId, beforeId, page);
	}

	@Override
	public long countUnread(Long recipientUserId) {
		return jpaRepository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
	}

	@Override
	public boolean existsByIdAndRecipient(Long id, Long recipientUserId) {
		return jpaRepository.existsByIdAndRecipientUserId(id, recipientUserId);
	}

	@Override
	public int markRead(Long id, Long recipientUserId, LocalDateTime readAtUtc) {
		return jpaRepository.markRead(id, recipientUserId, readAtUtc);
	}

	@Override
	public int markAllRead(Long recipientUserId, LocalDateTime readAtUtc) {
		return jpaRepository.markAllRead(recipientUserId, readAtUtc);
	}
}

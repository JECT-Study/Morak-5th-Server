package com.morak.feature.notification.support;

import com.morak.feature.notification.domain.entity.Notification;
import com.morak.feature.notification.domain.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;

/** 서비스 단위 테스트용 저장소. dedup_key 유니크와 id 부여를 흉내 낸다. */
public class InMemoryNotificationRepository implements NotificationRepository {

	public final List<Notification> saved = new ArrayList<>();
	private final Set<String> dedupKeys = new HashSet<>();
	private long nextId = 1;

	@Override
	public boolean saveIfAbsent(Notification notification) {
		if (notification.getDedupKey() != null && !dedupKeys.add(notification.getDedupKey())) {
			return false;
		}
		ReflectionTestUtils.setField(notification, "id", nextId++);
		saved.add(notification);
		return true;
	}

	@Override
	public List<Notification> findByRecipient(Long recipientUserId, Long beforeId, int limit) {
		return saved.stream()
				.filter(n -> n.getRecipientUserId().equals(recipientUserId))
				.filter(n -> beforeId == null || n.getId() < beforeId)
				.sorted(Comparator.comparing(Notification::getId).reversed())
				.limit(limit)
				.toList();
	}

	@Override
	public long countUnread(Long recipientUserId) {
		return saved.stream()
				.filter(n -> n.getRecipientUserId().equals(recipientUserId) && !n.isRead())
				.count();
	}

	@Override
	public boolean existsByIdAndRecipient(Long id, Long recipientUserId) {
		return saved.stream().anyMatch(n -> n.getId().equals(id) && n.getRecipientUserId().equals(recipientUserId));
	}

	@Override
	public int markRead(Long id, Long recipientUserId, LocalDateTime readAtUtc) {
		return (int) saved.stream()
				.filter(n -> n.getId().equals(id) && n.getRecipientUserId().equals(recipientUserId) && !n.isRead())
				.peek(n -> ReflectionTestUtils.setField(n, "readAt", readAtUtc))
				.count();
	}

	@Override
	public int markAllRead(Long recipientUserId, LocalDateTime readAtUtc) {
		return (int) saved.stream()
				.filter(n -> n.getRecipientUserId().equals(recipientUserId) && !n.isRead())
				.peek(n -> ReflectionTestUtils.setField(n, "readAt", readAtUtc))
				.count();
	}
}

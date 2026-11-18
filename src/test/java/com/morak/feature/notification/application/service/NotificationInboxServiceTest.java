package com.morak.feature.notification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationApproved;
import com.morak.feature.notification.application.dto.NotificationListResult;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import com.morak.feature.notification.support.InMemoryNotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationInboxServiceTest {

	private static final Long USER = 10L;
	private static final Long OTHER = 20L;
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneOffset.UTC);

	private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
	private final NotificationService writer = new NotificationService(repository, CLOCK, event -> {});
	private final NotificationInboxService inbox = new NotificationInboxService(repository, CLOCK);

	private void add(Long recipient, int count) {
		for (int i = 0; i < count; i++) {
			writer.notify(new NotifyCommand(
					recipient, new ParticipationApproved("챌린지" + i), "CHALLENGE", 5L, "K:" + recipient + ":" + i));
		}
	}

	private static List<Long> ids(NotificationListResult result) {
		return result.notifications().stream().map(NotificationListResult.Entry::id).toList();
	}

	@Test
	void listsOnlyMyNotificationsNewestFirst() {
		add(USER, 3);
		add(OTHER, 2);

		NotificationListResult result = inbox.getNotifications(USER, null, null);

		assertEquals(List.of(3L, 2L, 1L), ids(result));
		assertNull(result.nextCursor());
	}

	@Test
	void listIncludesTargetAndReadState() {
		add(USER, 1);

		NotificationListResult.Entry entry = inbox.getNotifications(USER, null, null).notifications().get(0);

		assertEquals("CHALLENGE", entry.targetType());
		assertEquals(5L, entry.targetId());
		assertFalse(entry.read());
		assertEquals(Instant.parse("2026-11-17T03:00:00Z"), entry.createdAt());
	}

	@Test
	void pagesWithCursor() {
		add(USER, 5);

		NotificationListResult first = inbox.getNotifications(USER, null, 2);
		assertEquals(List.of(5L, 4L), ids(first));
		assertEquals(4L, first.nextCursor());

		NotificationListResult second = inbox.getNotifications(USER, first.nextCursor(), 2);
		assertEquals(List.of(3L, 2L), ids(second));
		assertEquals(2L, second.nextCursor());

		NotificationListResult last = inbox.getNotifications(USER, second.nextCursor(), 2);
		assertEquals(List.of(1L), ids(last));
		assertNull(last.nextCursor());
	}

	@Test
	void exactlyFullPageHasNoNextCursor() {
		add(USER, 2);

		assertNull(inbox.getNotifications(USER, null, 2).nextCursor());
	}

	@Test
	void limitIsClampedToAllowedRange() {
		add(USER, 105);

		assertEquals(20, inbox.getNotifications(USER, null, null).notifications().size());
		assertEquals(1, inbox.getNotifications(USER, null, 0).notifications().size());
		assertEquals(1, inbox.getNotifications(USER, null, -5).notifications().size());
		assertEquals(100, inbox.getNotifications(USER, null, 1_000).notifications().size());
	}

	@Test
	void emptyInboxReturnsEmptyList() {
		NotificationListResult result = inbox.getNotifications(USER, null, null);

		assertTrue(result.notifications().isEmpty());
		assertNull(result.nextCursor());
	}

	@Test
	void rejectsNonPositiveCursor() {
		BusinessException e = assertThrows(BusinessException.class, () -> inbox.getNotifications(USER, 0L, null));

		assertEquals(NotificationErrorCode.INVALID_CURSOR, e.getErrorCode());
	}

	@Test
	void countsOnlyMyUnreadNotifications() {
		add(USER, 3);
		add(OTHER, 4);
		inbox.markRead(USER, 1L);

		assertEquals(2, inbox.countUnread(USER));
		assertEquals(4, inbox.countUnread(OTHER));
		assertEquals(0, inbox.countUnread(99L));
	}

	@Test
	void markReadChangesOnlyThatNotification() {
		add(USER, 3);

		inbox.markRead(USER, 2L);

		List<NotificationListResult.Entry> entries = inbox.getNotifications(USER, null, null).notifications();
		assertFalse(entries.get(0).read());
		assertTrue(entries.get(1).read());
		assertFalse(entries.get(2).read());
	}

	@Test
	void markReadIsIdempotentAndKeepsFirstReadTime() {
		add(USER, 1);
		inbox.markRead(USER, 1L);
		LocalDateTime firstReadAt = repository.saved.get(0).getReadAt();

		NotificationInboxService later = new NotificationInboxService(
				repository, Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneOffset.UTC));
		later.markRead(USER, 1L);

		assertEquals(LocalDateTime.parse("2026-11-17T03:00:00"), firstReadAt);
		assertEquals(firstReadAt, repository.saved.get(0).getReadAt());
	}

	@Test
	void markReadRejectsMissingAndOthersNotificationsWithSameError() {
		add(OTHER, 1);

		BusinessException others = assertThrows(BusinessException.class, () -> inbox.markRead(USER, 1L));
		BusinessException missing = assertThrows(BusinessException.class, () -> inbox.markRead(USER, 999L));

		assertEquals(NotificationErrorCode.NOTIFICATION_NOT_FOUND, others.getErrorCode());
		assertEquals(NotificationErrorCode.NOTIFICATION_NOT_FOUND, missing.getErrorCode());
		assertFalse(repository.saved.get(0).isRead());
	}

	@Test
	void markAllReadChangesOnlyMyUnreadNotifications() {
		add(USER, 3);
		add(OTHER, 2);
		inbox.markRead(USER, 1L);

		int changed = inbox.markAllRead(USER);

		assertEquals(2, changed);
		assertEquals(0, inbox.countUnread(USER));
		assertEquals(2, inbox.countUnread(OTHER));
		assertEquals(0, inbox.markAllRead(USER));
	}
}

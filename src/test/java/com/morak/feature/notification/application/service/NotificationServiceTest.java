package com.morak.feature.notification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationApproved;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationRejected;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.domain.entity.Notification;
import com.morak.feature.notification.domain.entity.NotificationType;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import com.morak.feature.notification.support.InMemoryNotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

	private static final String TITLE = "매일 아침 러닝";

	private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
	private final NotificationService service = new NotificationService(
			repository, Clock.fixed(Instant.parse("2026-11-17T03:00:00.123456789Z"), ZoneOffset.UTC));

	@Test
	void savesNotificationWithAssembledMessageAndTarget() {
		boolean created = service.notify(new NotifyCommand(
				10L, new ParticipationApproved(TITLE), "CHALLENGE", 5L, "PARTICIPATION_APPROVED:7"));

		assertTrue(created);
		assertEquals(1, repository.saved.size());
		Notification saved = repository.saved.get(0);
		assertEquals(10L, saved.getRecipientUserId());
		assertEquals(NotificationType.PARTICIPATION_APPROVED, saved.getType());
		assertEquals("참여 확정", saved.getTitle());
		assertEquals("매일 아침 러닝 참여가 확정됐어요", saved.getBody());
		assertEquals("CHALLENGE", saved.getTargetType());
		assertEquals(5L, saved.getTargetId());
		assertEquals("PARTICIPATION_APPROVED:7", saved.getDedupKey());
		assertNull(saved.getReadAt());
	}

	@Test
	void createdAtIsUtcTruncatedToMicroseconds() {
		service.notify(new NotifyCommand(10L, new ParticipationApproved(TITLE), null, null, null));

		assertEquals(LocalDateTime.parse("2026-11-17T03:00:00.123456"), repository.saved.get(0).getCreatedAt());
	}

	@Test
	void sameDedupKeyCalledTwiceIsSavedOnce() {
		NotifyCommand command =
				new NotifyCommand(10L, new ParticipationApproved(TITLE), null, null, "PARTICIPATION_APPROVED:7");

		boolean first = service.notify(command);
		boolean second = service.notify(command);

		assertTrue(first);
		assertFalse(second);
		assertEquals(1, repository.saved.size());
	}

	@Test
	void differentDedupKeysAreSavedSeparately() {
		service.notify(new NotifyCommand(10L, new ParticipationApproved(TITLE), null, null, "KEY:1"));
		service.notify(new NotifyCommand(10L, new ParticipationApproved(TITLE), null, null, "KEY:2"));

		assertEquals(2, repository.saved.size());
	}

	@Test
	void commandsWithoutDedupKeyAreAlwaysSaved() {
		NotifyCommand command = new NotifyCommand(10L, new ParticipationApproved(TITLE), null, null, null);

		assertTrue(service.notify(command));
		assertTrue(service.notify(command));
		assertEquals(2, repository.saved.size());
	}

	@Test
	void pointSentenceIsIncludedOnlyWhenPointsWereHeld() {
		service.notify(new NotifyCommand(10L, new ParticipationRejected(TITLE, 1_000L), null, null, "R:1"));
		service.notify(new NotifyCommand(11L, new ParticipationRejected(TITLE, null), null, null, "R:2"));

		assertTrue(repository.saved.get(0).getBody().contains("걸어 둔 1,000P는 전액 반환됐어요"));
		assertFalse(repository.saved.get(1).getBody().contains("반환"));
	}

	@Test
	void rejectsMessageLongerThanColumnLimit() {
		NotificationContent tooLong = new ParticipationApproved("가".repeat(Notification.MAX_BODY_LENGTH));

		BusinessException e = assertThrows(
				BusinessException.class, () -> service.notify(new NotifyCommand(10L, tooLong, null, null, null)));

		assertEquals(NotificationErrorCode.INVALID_COMMAND, e.getErrorCode());
		assertTrue(repository.saved.isEmpty());
	}
}

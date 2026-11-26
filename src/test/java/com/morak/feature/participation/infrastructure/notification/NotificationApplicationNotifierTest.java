package com.morak.feature.participation.infrastructure.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.morak.feature.notification.application.dto.NotificationContent.ParticipationRequested;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationApplicationNotifierTest {

	private final NotificationService notifications = mock(NotificationService.class);
	private final NotificationApplicationNotifier notifier = new NotificationApplicationNotifier(notifications);

	@Test
	void requestedGoesToTheHostWithPerApplicationDedupKey() {
		notifier.requested(1L, 10L, 44L, "아침 독서");

		ArgumentCaptor<NotifyCommand> captor = ArgumentCaptor.forClass(NotifyCommand.class);
		verify(notifications).notify(captor.capture());
		NotifyCommand command = captor.getValue();
		assertEquals(1L, command.recipientUserId());
		assertEquals(new ParticipationRequested("아침 독서"), command.content());
		assertEquals("CHALLENGE", command.targetType());
		assertEquals(10L, command.targetId());
		assertEquals("PARTICIPATION_REQUESTED:44", command.dedupKey());
	}
}

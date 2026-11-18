package com.morak.feature.challenge.infrastructure.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import com.morak.feature.notification.application.dto.NotificationContent.ApplicationInvalidated;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationInvalidatedApplicationNoticeTest {

	private final NotificationService notifications = mock(NotificationService.class);
	private final ChallengeRepository challenges = mock(ChallengeRepository.class);
	private final NotificationInvalidatedApplicationNotice notice =
			new NotificationInvalidatedApplicationNotice(notifications, challenges);

	@Test
	void sendsInvalidatedNoticeToHostWithTargetAndPerApplicationDedupKey() {
		Challenge challenge = mock(Challenge.class);
		when(challenge.getTitle()).thenReturn("아침 독서");
		when(challenges.findById(10L)).thenReturn(Optional.of(challenge));

		notice.send(3L, 10L, 44L, Instant.parse("2026-11-18T01:00:00Z"));

		ArgumentCaptor<NotifyCommand> captor = ArgumentCaptor.forClass(NotifyCommand.class);
		verify(notifications).notify(captor.capture());
		NotifyCommand command = captor.getValue();
		assertEquals(3L, command.recipientUserId());
		assertEquals(new ApplicationInvalidated("아침 독서"), command.content());
		assertEquals("CHALLENGE", command.targetType());
		assertEquals(10L, command.targetId());
		assertEquals("APPLICATION_INVALIDATED:44", command.dedupKey());
	}

	@Test
	void missingChallengeFailsWithoutSendingAnything() {
		when(challenges.findById(10L)).thenReturn(Optional.empty());

		assertThrows(BusinessException.class, () -> notice.send(3L, 10L, 44L, Instant.now()));

		verify(notifications, never()).notify(org.mockito.ArgumentMatchers.any());
	}
}

package com.morak.feature.notification.application.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationApproved;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class NotifyCommandTest {

	private static final NotificationContent CONTENT = new ParticipationApproved("매일 아침 러닝");

	@Test
	void acceptsCommandWithTargetAndDedupKey() {
		assertDoesNotThrow(() -> new NotifyCommand(10L, CONTENT, "CHALLENGE", 5L, "PARTICIPATION_APPROVED:7"));
	}

	@Test
	void acceptsCommandWithoutTargetAndDedupKey() {
		assertDoesNotThrow(() -> new NotifyCommand(10L, CONTENT, null, null, null));
	}

	@Test
	void rejectsInvalidRecipient() {
		assertInvalid(() -> new NotifyCommand(null, CONTENT, null, null, null));
		assertInvalid(() -> new NotifyCommand(0L, CONTENT, null, null, null));
	}

	@Test
	void rejectsMissingContent() {
		assertInvalid(() -> new NotifyCommand(10L, null, null, null, null));
	}

	@Test
	void targetTypeAndTargetIdMustComeTogether() {
		assertInvalid(() -> new NotifyCommand(10L, CONTENT, "CHALLENGE", null, null));
		assertInvalid(() -> new NotifyCommand(10L, CONTENT, null, 5L, null));
	}

	@Test
	void rejectsTooLongTargetTypeAndDedupKey() {
		assertInvalid(() -> new NotifyCommand(10L, CONTENT, "T".repeat(31), 5L, null));
		assertInvalid(() -> new NotifyCommand(10L, CONTENT, null, null, "K".repeat(101)));
		assertInvalid(() -> new NotifyCommand(10L, CONTENT, null, null, " "));
		assertDoesNotThrow(() -> new NotifyCommand(10L, CONTENT, "T".repeat(30), 5L, "K".repeat(100)));
	}

	private static void assertInvalid(Executable executable) {
		BusinessException e = assertThrows(BusinessException.class, executable);
		assertEquals(NotificationErrorCode.INVALID_COMMAND, e.getErrorCode());
	}
}

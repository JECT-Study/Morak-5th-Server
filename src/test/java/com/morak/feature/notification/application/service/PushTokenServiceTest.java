package com.morak.feature.notification.application.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.domain.entity.PushChannel;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import com.morak.feature.notification.support.InMemoryPushTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class PushTokenServiceTest {

	private static final Long USER = 10L;
	private static final Long OTHER = 20L;
	private static final String TOKEN = "ExponentPushToken[abc123_-XYZ]";

	private final InMemoryPushTokenRepository repository = new InMemoryPushTokenRepository();
	private final PushTokenService service = new PushTokenService(
			repository, Clock.fixed(Instant.parse("2026-11-18T03:00:00.123456789Z"), ZoneOffset.UTC));

	@Test
	void registersExpoTokenWithChannelAndUtcTime() {
		service.register(USER, TOKEN, "Expo/1.0 (iPhone)");

		InMemoryPushTokenRepository.Stored stored = repository.tokens.get(TOKEN);
		assertEquals(USER, stored.userId());
		assertEquals(PushChannel.EXPO, stored.channel());
		assertEquals("Expo/1.0 (iPhone)", stored.userAgent());
		assertEquals(LocalDateTime.parse("2026-11-18T03:00:00.123456"), stored.registeredAt());
	}

	@Test
	void acceptsBothExpoTokenPrefixes() {
		assertDoesNotThrow(() -> service.register(USER, "ExponentPushToken[aaa]", null));
		assertDoesNotThrow(() -> service.register(USER, "ExpoPushToken[bbb]", null));
	}

	@Test
	void sameTokenRegisteredTwiceIsStoredOnce() {
		service.register(USER, TOKEN, null);
		service.register(USER, TOKEN, null);

		assertEquals(1, repository.tokens.size());
	}

	@Test
	void sameTokenFromAnotherUserOverwritesOwner() {
		service.register(USER, TOKEN, null);
		service.register(OTHER, TOKEN, null);

		assertEquals(1, repository.tokens.size());
		assertEquals(OTHER, repository.tokens.get(TOKEN).userId());
	}

	@Test
	void rejectsInvalidTokenFormats() {
		for (String bad : List.of(
				"", " ", "abc", "FCMtoken123", "ExponentPushToken[]", "ExponentPushToken[a b]",
				"ExponentPushToken[abc", "xExponentPushToken[abc]", "ExponentPushToken[abc]x")) {
			assertInvalid(() -> service.register(USER, bad, null));
		}
		assertInvalid(() -> service.register(USER, null, null));
		assertInvalid(() -> service.register(USER, "ExponentPushToken[" + "a".repeat(700) + "]", null));
		assertTrue(repository.tokens.isEmpty());
	}

	@Test
	void blankOrTooLongUserAgentIsHandledWithoutFailing() {
		service.register(USER, "ExponentPushToken[a]", "   ");
		service.register(USER, "ExponentPushToken[b]", "U".repeat(300));

		assertNull(repository.tokens.get("ExponentPushToken[a]").userAgent());
		assertEquals(255, repository.tokens.get("ExponentPushToken[b]").userAgent().length());
	}

	@Test
	void unregisterDeletesOwnToken() {
		service.register(USER, TOKEN, null);

		service.unregister(USER, TOKEN);

		assertTrue(repository.tokens.isEmpty());
	}

	@Test
	void unregisterOfMissingTokenSucceeds() {
		assertDoesNotThrow(() -> service.unregister(USER, TOKEN));
	}

	@Test
	void unregisterDoesNotDeleteAnotherUsersToken() {
		service.register(OTHER, TOKEN, null);

		assertDoesNotThrow(() -> service.unregister(USER, TOKEN));

		assertFalse(repository.tokens.isEmpty());
		assertEquals(OTHER, repository.tokens.get(TOKEN).userId());
	}

	@Test
	void unregisterRejectsBlankToken() {
		assertInvalid(() -> service.unregister(USER, null));
		assertInvalid(() -> service.unregister(USER, " "));
	}

	private static void assertInvalid(Executable executable) {
		BusinessException e = assertThrows(BusinessException.class, executable);
		assertEquals(NotificationErrorCode.INVALID_PUSH_TOKEN, e.getErrorCode());
	}
}

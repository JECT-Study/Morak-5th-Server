package com.morak.feature.auth.domain.entity;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);

	@Test
	void usableOnlyBeforeExpiration() {
		RefreshToken token = new RefreshToken(7L, "hash", NOW);

		assertEquals(7L, token.getUserId());
		assertTrue(token.isUsableAt(NOW.minusNanos(1)));
		assertFalse(token.isUsableAt(NOW));
		assertFalse(token.isUsableAt(NOW.plusNanos(1)));
	}

	@Test
	void revokedTokenCannotBeUsedAndOtherLoginIsUnaffected() {
		RefreshToken token = new RefreshToken(7L, "hash", NOW.plusDays(1));
		RefreshToken otherLogin = new RefreshToken(7L, "other-hash", NOW.plusDays(1));

		token.revoke(NOW);
		token.revoke(NOW.plusMinutes(1));

		assertFalse(token.isUsableAt(NOW));
		assertFalse(token.isUsableAt(NOW.plusMinutes(1)));
		assertTrue(otherLogin.isUsableAt(NOW));
	}
}

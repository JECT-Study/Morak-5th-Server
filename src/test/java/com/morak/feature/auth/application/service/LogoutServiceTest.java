package com.morak.feature.auth.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.auth.application.port.out.UserAuthPort;
import com.morak.feature.auth.domain.entity.RefreshToken;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogoutServiceTest {
	private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
	private final ServiceTokenIssuer tokens = mock(ServiceTokenIssuer.class);
	private final LogoutService service = new LogoutService(repository, tokens);

	LogoutServiceTest() {
		when(tokens.hash("refresh")).thenReturn("hash");
	}

	@Test
	void revokesOnlyRequestedLoginAndRejectsSubsequentRefresh() {
		RefreshToken current = new RefreshToken(7L, "hash", LocalDateTime.now().plusDays(1));
		RefreshToken otherLogin = new RefreshToken(7L, "other-hash", LocalDateTime.now().plusDays(1));
		when(repository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(current));

		service.logout("refresh");

		assertFalse(current.isUsableAt(LocalDateTime.now()));
		assertTrue(otherLogin.isUsableAt(LocalDateTime.now()));
		UserAuthPort users = mock(UserAuthPort.class);
		RefreshTokenService refresh = new RefreshTokenService(repository, users, tokens, Duration.ofDays(30));
		assertEquals(AuthErrorCode.INVALID_REFRESH_TOKEN,
				assertThrows(BusinessException.class, () -> refresh.refresh("refresh")).getErrorCode());
		verifyNoInteractions(users);
		verify(tokens, never()).createRefreshToken();
		verify(repository, never()).save(any());
	}

	@Test
	void repeatedLogoutSucceedsWithoutChangingOriginalRevocationTime() {
		RefreshToken current = new RefreshToken(7L, "hash", LocalDateTime.now().plusDays(1));
		LocalDateTime revokedAt = LocalDateTime.now().minusMinutes(1);
		current.revoke(revokedAt);
		when(repository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(current));

		assertDoesNotThrow(() -> service.logout("refresh"));
		assertDoesNotThrow(() -> service.logout("refresh"));

		assertEquals(revokedAt, ReflectionTestUtils.getField(current, "revokedAt"));
	}

	@Test
	void expiredTokenLogoutSucceeds() {
		RefreshToken expired = new RefreshToken(7L, "hash", LocalDateTime.now().minusDays(1));
		when(repository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(expired));

		assertDoesNotThrow(() -> service.logout("refresh"));

		assertNotNull(ReflectionTestUtils.getField(expired, "revokedAt"));
		assertFalse(expired.isUsableAt(LocalDateTime.now()));
	}

	@Test
	void unknownTokenLogoutSucceeds() {
		when(repository.findByTokenHashForUpdate("hash")).thenReturn(Optional.empty());

		assertDoesNotThrow(() -> service.logout("refresh"));

		verify(repository, never()).save(any());
	}
}

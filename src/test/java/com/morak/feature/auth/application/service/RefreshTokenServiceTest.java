package com.morak.feature.auth.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.result.AccessToken;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.auth.application.port.out.UserAuthPort;
import com.morak.feature.auth.application.port.out.UserAuthPort.UserState;
import com.morak.feature.auth.domain.entity.RefreshToken;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import com.morak.feature.auth.domain.vo.AuthUserId;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
	private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
	private final UserAuthPort users = mock(UserAuthPort.class);
	private final ServiceTokenIssuer tokens = mock(ServiceTokenIssuer.class);
	private final RefreshTokenService service = new RefreshTokenService(repository, users, tokens, Duration.ofDays(30));
	private final AuthUserId userId = new AuthUserId(7L);
	private final RefreshToken previous = new RefreshToken(7L, "old-hash", LocalDateTime.now().plusDays(1));

	RefreshTokenServiceTest() {
		when(tokens.hash("old")).thenReturn("old-hash");
		when(repository.findByTokenHashForUpdate("old-hash")).thenReturn(Optional.of(previous));
	}

	@Test
	void rotatesTokenUsingCurrentOnboardingStateAndStoresOnlyHash() {
		when(users.findUser(userId)).thenReturn(Optional.of(new UserState(userId, true, true)));
		Instant expiresAt = Instant.parse("2026-11-06T12:30:00Z");
		when(tokens.createAccessToken(userId, true)).thenReturn(new AccessToken("access", expiresAt));
		when(tokens.createRefreshToken()).thenReturn("new");
		when(tokens.hash("new")).thenReturn("new-hash");
		when(tokens.accessValiditySeconds()).thenReturn(1800L);
		LocalDateTime before = LocalDateTime.now();

		var result = service.refresh("old");

		assertEquals("access", result.accessToken());
		assertEquals("new", result.refreshToken());
		assertEquals("Bearer", result.tokenType());
		assertEquals(1800L, result.accessTokenExpiresIn());
		assertEquals(expiresAt, result.accessTokenExpiresAt());
		assertTrue(result.onboarded());
		assertFalse(previous.isUsableAt(LocalDateTime.now()));
		ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
		verify(repository).save(saved.capture());
		assertEquals(7L, saved.getValue().getUserId());
		assertEquals("new-hash", ReflectionTestUtils.getField(saved.getValue(), "tokenHash"));
		LocalDateTime expiry = (LocalDateTime) ReflectionTestUtils.getField(saved.getValue(), "expiresAt");
		assertFalse(expiry.isBefore(before.plusDays(30)));
		assertFalse(expiry.isAfter(LocalDateTime.now().plusDays(30)));

		assertFailure(AuthErrorCode.INVALID_REFRESH_TOKEN);
		verify(tokens, times(1)).createRefreshToken();
		verify(repository, times(1)).save(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "expired", "revoked"})
	void rejectsUnusableTokenWithoutIssuingNewTokens(String state) {
		if (state.equals("missing")) {
			when(repository.findByTokenHashForUpdate("old-hash")).thenReturn(Optional.empty());
		} else if (state.equals("expired")) {
			when(repository.findByTokenHashForUpdate("old-hash")).thenReturn(
					Optional.of(new RefreshToken(7L, "old-hash", LocalDateTime.now().minusSeconds(1))));
		} else {
			previous.revoke(LocalDateTime.now());
		}
		assertFailure(AuthErrorCode.INVALID_REFRESH_TOKEN);
		verifyNoInteractions(users);
		verify(tokens, never()).createRefreshToken();
		verify(repository, never()).save(any());
	}

	@Test
	void missingUserIsUnauthorized() {
		when(users.findUser(userId)).thenReturn(Optional.empty());
		assertFailure(AuthErrorCode.INVALID_REFRESH_TOKEN);
		assertTrue(previous.isUsableAt(LocalDateTime.now()));
		verify(tokens, never()).createRefreshToken();
	}

	@Test
	void withdrawnUserIsForbidden() {
		when(users.findUser(userId)).thenReturn(Optional.of(new UserState(userId, false, true)));
		assertFailure(AuthErrorCode.WITHDRAWN_USER);
		assertTrue(previous.isUsableAt(LocalDateTime.now()));
		verify(tokens, never()).createRefreshToken();
	}

	private void assertFailure(AuthErrorCode expected) {
		assertEquals(expected, assertThrows(BusinessException.class, () -> service.refresh("old")).getErrorCode());
	}
}

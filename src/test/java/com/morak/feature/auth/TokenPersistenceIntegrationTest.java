package com.morak.feature.auth;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.result.IssuedTokens;
import com.morak.feature.auth.application.service.LogoutService;
import com.morak.feature.auth.application.service.RefreshTokenService;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import com.morak.feature.auth.infrastructure.token.ServiceTokenProvider;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses real PostgreSQL transactions; requires a running Docker engine. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class TokenPersistenceIntegrationTest {
	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {
		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private RefreshTokenService refresh;
	@Autowired private LogoutService logout;
	@Autowired private JwtDecoder decoder;
	@MockitoSpyBean private ServiceTokenProvider tokens;
	private long userId;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.refresh_token").update();
		jdbc.sql("DELETE FROM morak.users").update();
		userId = jdbc.sql("""
				INSERT INTO morak.users (status, onboarded_at, created_at, updated_at)
				VALUES ('ACTIVE', now(), now(), now()) RETURNING id
				""").query(Long.class).single();
	}

	@Test
	void concurrentRefreshIssuesExactlyOneReplacement() throws Exception {
		insertToken("old", LocalDateTime.now().plusDays(1));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> refreshAfterSignal(ready, start));
			var second = executor.submit(() -> refreshAfterSignal(ready, start));
			try {
				assertTrue(ready.await(5, TimeUnit.SECONDS));
			} finally {
				start.countDown();
			}
			IssuedTokens a = first.get(15, TimeUnit.SECONDS);
			IssuedTokens b = second.get(15, TimeUnit.SECONDS);
			assertTrue((a == null) != (b == null), "Exactly one refresh must succeed");
			IssuedTokens winner = a != null ? a : b;
			assertTrue(isRevoked("old"));
			assertFalse(isRevoked(winner.refreshToken()));
			assertEquals(2L, tokenCount());
			assertTrue(winner.onboarded());
			assertRejected("old");
		}
	}

	@Test
	void replacementInsertFailureRollsBackPreviousRevocation() {
		insertToken("old", LocalDateTime.now().plusDays(1));
		// Force a real database unique constraint failure after the previous token is revoked.
		doReturn("old").when(tokens).createRefreshToken();
		try {
			assertThrows(DataIntegrityViolationException.class, () -> refresh.refresh("old"));
		} finally {
			doCallRealMethod().when(tokens).createRefreshToken();
		}
		assertFalse(isRevoked("old"));
		assertEquals(1L, tokenCount());
		IssuedTokens retried = refresh.refresh("old");
		assertTrue(isRevoked("old"));
		assertFalse(isRevoked(retried.refreshToken()));
	}

	@Test
	void logoutPersistsRevocationAndPreservesOtherLoginAndExistingAccessToken() {
		insertToken("old", LocalDateTime.now().plusDays(1));
		IssuedTokens login = refresh.refresh("old");
		insertToken("other-login", LocalDateTime.now().plusDays(1));

		logout.logout(login.refreshToken());
		LocalDateTime revokedAt = revocationTime(login.refreshToken());
		logout.logout(login.refreshToken());
		logout.logout("not-registered");

		assertEquals(revokedAt, revocationTime(login.refreshToken()));
		assertRejected(login.refreshToken());
		assertFalse(isRevoked("other-login"));
		assertNotNull(refresh.refresh("other-login"));
		assertEquals(Long.toString(userId), decoder.decode(login.accessToken()).getSubject());
	}

	@Test
	void expiredTokenCannotRefreshButCanLogout() {
		insertToken("expired", LocalDateTime.now().minusSeconds(1));
		assertRejected("expired");
		assertDoesNotThrow(() -> logout.logout("expired"));
		assertTrue(isRevoked("expired"));
		assertEquals(1L, tokenCount());
	}

	private IssuedTokens refreshAfterSignal(CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		assertTrue(start.await(5, TimeUnit.SECONDS));
		try {
			return refresh.refresh("old");
		} catch (BusinessException exception) {
			assertEquals(AuthErrorCode.INVALID_REFRESH_TOKEN, exception.getErrorCode());
			return null;
		}
	}

	private void insertToken(String value, LocalDateTime expiry) {
		jdbc.sql("""
				INSERT INTO morak.refresh_token (user_id, token_hash, expires_at, created_at)
				VALUES (:userId, :hash, :expiry, now())
				""").param("userId", userId).param("hash", tokens.hash(value)).param("expiry", expiry).update();
	}

	private boolean isRevoked(String value) {
		return jdbc.sql("SELECT revoked_at IS NOT NULL FROM morak.refresh_token WHERE token_hash = :hash")
				.param("hash", tokens.hash(value)).query(Boolean.class).single();
	}

	private LocalDateTime revocationTime(String value) {
		return jdbc.sql("SELECT revoked_at FROM morak.refresh_token WHERE token_hash = :hash")
				.param("hash", tokens.hash(value)).query(LocalDateTime.class).single();
	}

	private long tokenCount() {
		return jdbc.sql("SELECT count(*) FROM morak.refresh_token").query(Long.class).single();
	}

	private void assertRejected(String value) {
		assertEquals(AuthErrorCode.INVALID_REFRESH_TOKEN,
				assertThrows(BusinessException.class, () -> refresh.refresh(value)).getErrorCode());
	}
}

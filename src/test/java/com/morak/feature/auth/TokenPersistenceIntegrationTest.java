package com.morak.feature.auth;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.result.IssuedTokens;
import com.morak.feature.auth.application.service.LogoutService;
import com.morak.feature.auth.application.service.RefreshTokenService;
import com.morak.feature.auth.application.service.RefreshTokenCleanupService;
import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import com.morak.feature.auth.infrastructure.token.ServiceTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
@SpringBootTest(properties = {
		"morak.auth.jwt.secret=integration-test-secret-integration-test-secret",
		"morak.auth.refresh-token-cleanup.cron=-",
		"morak.auth.refresh-token-cleanup.retention=P7D"
})
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
	@Autowired private RefreshTokenCleanupService cleanup;
	@Autowired private RefreshTokenRepository repository;
	@Autowired private PlatformTransactionManager transactionManager;
	@PersistenceContext private EntityManager entityManager;
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

	/** Holds the winning transaction open and observes the competing request waiting in PostgreSQL. */
	@Test
	void concurrentRefreshIssuesExactlyOneReplacement() throws Exception {
		insertToken("old", LocalDateTime.now().plusDays(1));
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		// The first service call has acquired the row lock before it starts issuing an Access Token.
		doAnswer(invocation -> {
			locked.countDown();
			assertTrue(release.await(15, TimeUnit.SECONDS), "First transaction was not released");
			return invocation.callRealMethod();
		}).when(tokens).createAccessToken(any(), anyBoolean());
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> refresh.refresh("old"));
			try {
				assertTrue(locked.await(5, TimeUnit.SECONDS));
				var second = executor.submit(() -> assertRejected("old"));
				awaitTokenLockWait();
				assertFalse(second.isDone(), "Second refresh must wait for the first transaction's lock");
				release.countDown();
				IssuedTokens winner = first.get(15, TimeUnit.SECONDS);
				second.get(15, TimeUnit.SECONDS);
				assertTrue(isRevoked("old"));
				assertFalse(isRevoked(winner.refreshToken()));
				assertEquals(2L, tokenCount());
				assertTrue(winner.onboarded());
				assertRejected("old");
			} finally {
				release.countDown();
			}
		} finally {
			doCallRealMethod().when(tokens).createAccessToken(any(), anyBoolean());
		}
	}

	/** A failed replacement INSERT must roll back the previous token's already flushed revocation. */
	@Test
	void replacementInsertFailureRollsBackPreviousRevocation() {
		insertToken("old", LocalDateTime.now().plusDays(1));
		doAnswer(invocation -> {
			entityManager.flush();
			assertTrue(isRevoked("old"), "The revocation must reach PostgreSQL before the replacement INSERT");
			return "old"; // Reuse the old hash to trigger the unique constraint.
		}).when(tokens).createRefreshToken();
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

	@Test
	void cleanupRetainsRecentExpiredAndUnexpiredTokensRegardlessOfRevocation() {
		insertToken("old-expired", LocalDateTime.now().minusDays(8));
		insertToken("old-revoked", LocalDateTime.now().minusDays(8));
		logout.logout("old-revoked");
		insertToken("recent-expired", LocalDateTime.now().minusDays(1));
		insertToken("active", LocalDateTime.now().plusDays(1));
		insertToken("revoked-unexpired", LocalDateTime.now().plusDays(1));
		logout.logout("revoked-unexpired");

		assertEquals(2, cleanup.cleanup());
		assertEquals(3L, tokenCount());
		assertFalse(isRevoked("recent-expired"));
		assertFalse(isRevoked("active"));
		assertTrue(isRevoked("revoked-unexpired"));
		assertEquals(0, cleanup.cleanup());
		assertRejected("old-expired");
		assertDoesNotThrow(() -> logout.logout("old-revoked"));
	}

	@Test
	void cleanupIncludesExactRetentionCutoffAndPreservesNewerTokens() {
		LocalDateTime cutoff = LocalDateTime.of(2026, 11, 1, 0, 0);
		insertToken("boundary", cutoff);
		insertToken("newer", cutoff.plusSeconds(1));
		Integer deleted = new TransactionTemplate(transactionManager).execute(status -> repository.deleteExpiredBefore(cutoff));
		assertEquals(1, deleted);
		assertEquals(1L, tokenCount());
		assertFalse(isRevoked("newer"));
	}

	/** Observes PostgreSQL lock contention so this test fails if the pessimistic lock is removed. */
	private void awaitTokenLockWait() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			boolean waiting = jdbc.sql("""
					SELECT EXISTS (
						SELECT 1 FROM pg_stat_activity
						WHERE datname = current_database() AND pid <> pg_backend_pid()
						AND wait_event_type = 'Lock' AND query ILIKE '%refresh_token%'
					)
					""").query(Boolean.class).single();
			if (waiting) {
				return;
			}
			Thread.sleep(25);
		}
		fail("Second refresh never waited for the token row lock");
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

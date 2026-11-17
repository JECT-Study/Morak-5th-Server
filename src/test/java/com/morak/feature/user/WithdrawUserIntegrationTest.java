package com.morak.feature.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.application.service.WithdrawUserService;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.vo.UserId;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 실제 PostgreSQL로 탈퇴 흐름(행 잠금, 비식별화, 세션 폐기, 참여 건수 조회 연결)을 검증한다. Docker가 필요하다. */
@SpringBootTest(properties = {
		"morak.auth.jwt.secret=integration-test-secret-integration-test-secret",
		"morak.auth.refresh-token-cleanup.cron=-",
		"morak.auth.refresh-token-cleanup.retention=P7D"
})
class WithdrawUserIntegrationTest {

	private static final long USER_ID = 1L;
	private static final long OTHER_USER_ID = 2L;

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {
		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private WithdrawUserService withdrawUserService;
	@Autowired private UserService userService;
	@Autowired private ServiceTokenIssuer tokens;
	@Autowired private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE morak.users, morak.user_interest, morak.refresh_token, morak.challenge, morak.participation CASCADE")
				.update();
		insertUser(USER_ID);
		insertUser(OTHER_USER_ID);
	}

	@Test
	void anonymizesRowDeletesInterestsAndRevokesOnlyThisUsersTokens() {
		insertToken(USER_ID, "login-1");
		insertToken(USER_ID, "login-2");
		insertToken(OTHER_USER_ID, "other-login");

		withdrawUserService.withdraw(new UserId(USER_ID));

		var row = jdbc.sql("SELECT email, nickname, profile_image_url, bio, profile_link_url, status, deleted_at, "
				+ "marketing_agreed, terms_agreed_at FROM morak.users WHERE id = :id")
				.param("id", USER_ID).query().singleRow();
		assertNull(row.get("email"));
		assertNull(row.get("nickname"));
		assertNull(row.get("profile_image_url"));
		assertNull(row.get("bio"));
		assertNull(row.get("profile_link_url"));
		assertEquals("WITHDRAWN", row.get("status"));
		assertNotNull(row.get("deleted_at"));
		assertEquals(true, row.get("marketing_agreed"));
		assertNotNull(row.get("terms_agreed_at"));
		assertEquals(0L, count("SELECT count(*) FROM morak.user_interest WHERE user_id = " + USER_ID));
		assertEquals(2L, count("SELECT count(*) FROM morak.user_interest WHERE user_id = " + OTHER_USER_ID));
		assertTrue(isRevoked("login-1"));
		assertTrue(isRevoked("login-2"));
		assertFalse(isRevoked("other-login"));
		assertEquals(
				jdbc.sql("SELECT deleted_at FROM morak.users WHERE id = :id").param("id", USER_ID)
						.query(LocalDateTime.class).single(),
				jdbc.sql("SELECT max(revoked_at) FROM morak.refresh_token WHERE user_id = :id").param("id", USER_ID)
						.query(LocalDateTime.class).single());
	}

	@Test
	void rejectsHostOfRecruitingChallengeAndLeavesEverythingUntouched() {
		long challengeId = insertChallenge(USER_ID, "RECRUITING");
		insertParticipation(challengeId, USER_ID, "HOST", "ACTIVE");
		insertToken(USER_ID, "login-1");

		BusinessException error = assertThrows(BusinessException.class,
				() -> withdrawUserService.withdraw(new UserId(USER_ID)));

		assertEquals(UserErrorCode.WITHDRAWAL_NOT_ALLOWED, error.getErrorCode());
		assertEquals("ACTIVE", status(USER_ID));
		assertEquals("a@example.com", jdbc.sql("SELECT email FROM morak.users WHERE id = :id")
				.param("id", USER_ID).query(String.class).single());
		assertFalse(isRevoked("login-1"));
	}

	@Test
	void rejectsActiveMemberOfInProgressChallenge() {
		long challengeId = insertChallenge(OTHER_USER_ID, "IN_PROGRESS");
		insertParticipation(challengeId, USER_ID, "MEMBER", "ACTIVE");

		BusinessException error = assertThrows(BusinessException.class,
				() -> withdrawUserService.withdraw(new UserId(USER_ID)));

		assertEquals(UserErrorCode.WITHDRAWAL_NOT_ALLOWED, error.getErrorCode());
		assertEquals("ACTIVE", status(USER_ID));
	}

	@Test
	void allowsWithdrawalWhenOnlyEndedChallengeRemains() {
		long challengeId = insertChallenge(OTHER_USER_ID, "COMPLETED");
		insertParticipation(challengeId, USER_ID, "MEMBER", "ACTIVE");

		withdrawUserService.withdraw(new UserId(USER_ID));

		assertEquals("WITHDRAWN", status(USER_ID));
	}

	/** 현재 구현이 승인 대기 신청을 탈퇴 불가 조건으로 보지 않음을 기록한다. 정책이 정해지면(위키 O-18 ②) 이 테스트를 바꾼다. */
	@Test
	void pendingApplicationDoesNotBlockWithdrawalForNow() {
		long challengeId = insertChallenge(OTHER_USER_ID, "RECRUITING");
		insertParticipation(challengeId, USER_ID, "MEMBER", "PENDING");

		withdrawUserService.withdraw(new UserId(USER_ID));

		assertEquals("WITHDRAWN", status(USER_ID));
		assertEquals(1L, count("SELECT count(*) FROM morak.participation WHERE user_id = " + USER_ID));
	}

	@Test
	void secondWithdrawalIsRejectedAsNotFound() {
		withdrawUserService.withdraw(new UserId(USER_ID));

		BusinessException error = assertThrows(BusinessException.class,
				() -> withdrawUserService.withdraw(new UserId(USER_ID)));

		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
	}

	/** 챌린지 개설과 같은 사용자 행 잠금을 쓰므로, 잠금을 쥔 트랜잭션이 끝나야 탈퇴가 진행된다. */
	@Test
	void waitsForTheUserRowLockHeldByAnotherTransaction() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				userService.lockActiveUser(new UserId(USER_ID));
				locked.countDown();
				try {
					assertTrue(release.await(15, TimeUnit.SECONDS), "잠금 해제 신호를 받지 못했다");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
			}));
			try {
				assertTrue(locked.await(5, TimeUnit.SECONDS));
				var withdrawal = executor.submit(() -> withdrawUserService.withdraw(new UserId(USER_ID)));
				Thread.sleep(700);
				assertFalse(withdrawal.isDone(), "탈퇴는 사용자 행 잠금이 풀릴 때까지 기다려야 한다");
				assertEquals("ACTIVE", status(USER_ID));
				release.countDown();
				holder.get(15, TimeUnit.SECONDS);
				withdrawal.get(15, TimeUnit.SECONDS);
				assertEquals("WITHDRAWN", status(USER_ID));
			} finally {
				release.countDown();
			}
		}
	}

	private void insertUser(long id) {
		jdbc.sql("""
				INSERT INTO morak.users (id, email, nickname, profile_image_url, bio, profile_link_url, status,
										terms_agreed_at, marketing_agreed, onboarded_at, created_at, updated_at)
				VALUES (:id, 'a@example.com', :nickname, 'https://example.com/a.png', '소개', 'https://example.com',
						'ACTIVE', now(), true, now(), now(), now())
				""").param("id", id).param("nickname", "user" + id).update();
		jdbc.sql("INSERT INTO morak.user_interest (user_id, interest_code) VALUES (:id, 'STUDY'), (:id, 'EXERCISE')")
				.param("id", id).update();
	}

	private void insertToken(long userId, String value) {
		jdbc.sql("""
				INSERT INTO morak.refresh_token (user_id, token_hash, expires_at, created_at)
				VALUES (:userId, :hash, now() + interval '1 day', now())
				""").param("userId", userId).param("hash", tokens.hash(value)).update();
	}

	private long insertChallenge(long ownerId, String status) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, status, start_date, end_date,
											capacity, created_at, updated_at)
				VALUES (:ownerId, '독서', 'READING', :code, :status, current_date, current_date + 6, 3, now(), now())
				RETURNING id
				""").param("ownerId", ownerId).param("code", String.format("%06d", Math.abs(ownerId * 7 + status.hashCode()) % 1_000_000))
				.param("status", status).query(Long.class).single();
	}

	private void insertParticipation(long challengeId, long userId, String role, String status) {
		jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at)
				VALUES (:challengeId, :userId, :role, :status, now())
				""").param("challengeId", challengeId).param("userId", userId).param("role", role)
				.param("status", status).update();
	}

	private boolean isRevoked(String value) {
		return jdbc.sql("SELECT revoked_at IS NOT NULL FROM morak.refresh_token WHERE token_hash = :hash")
				.param("hash", tokens.hash(value)).query(Boolean.class).single();
	}

	private String status(long userId) {
		return jdbc.sql("SELECT status FROM morak.users WHERE id = :id").param("id", userId).query(String.class).single();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}
}

package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.application.command.ParticipationCommand.InvalidatedApplication;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 참가자 변경 API를 실제 PostgreSQL(Testcontainers)로 검증한다. Docker가 실행 중이어야 한다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class ParticipationCommandIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");
	private static final LocalDateTime NOW_UTC = LocalDateTime.of(2026, 11, 13, 1, 0);

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private ParticipationCommand command;
	@Autowired private PlatformTransactionManager transactionManager;

	@BeforeEach
	void clear() {
		jdbc.sql("DELETE FROM morak.chat_message").update();
		jdbc.sql("DELETE FROM morak.participation_result").update();
		jdbc.sql("DELETE FROM morak.participation").update();
	}

	@Test
	void registerHostStoresActiveHostWithJoinedAt() {
		inTransaction(() -> command.registerHost(10L, 1L, NOW));

		assertEquals("HOST:ACTIVE:NONE", jdbc.sql("""
				SELECT role || ':' || status || ':' || point_status FROM morak.participation
				WHERE challenge_id = 10 AND user_id = 1
				""").query(String.class).single());
		assertEquals(NOW_UTC, jdbc.sql("SELECT joined_at FROM morak.participation WHERE user_id = 1")
				.query(LocalDateTime.class).single());
	}

	@Test
	void registerHostRejectsDuplicateForSameChallengeAndUser() {
		inTransaction(() -> command.registerHost(10L, 1L, NOW));

		BusinessException error = assertThrows(BusinessException.class,
				() -> inTransaction(() -> command.registerHost(10L, 1L, NOW)));

		assertEquals(ParticipationErrorCode.ALREADY_APPLIED, error.getErrorCode());
	}

	@Test
	void registerHostRollsBackWithCallerTransaction() {
		assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
			command.registerHost(10L, 1L, NOW);
			throw new IllegalStateException("개설 실패");
		}));

		assertEquals(0L, count("SELECT count(*) FROM morak.participation"));
	}

	@Test
	void commandsRequireCallerTransaction() {
		assertThrows(IllegalTransactionStateException.class, () -> command.registerHost(10L, 1L, NOW));
		assertThrows(IllegalTransactionStateException.class, () -> command.invalidatePendingWithoutPoints(7L));
	}

	@Test
	void invalidatesOnlyThePendingApplicationsOfTheUser() {
		insert(20L, 3L, "HOST", "ACTIVE", "NONE");
		insert(21L, 4L, "HOST", "ACTIVE", "NONE");
		long mine20 = insert(20L, 7L, "MEMBER", "PENDING", "NONE");
		long mine21 = insert(21L, 7L, "MEMBER", "PENDING", "NONE");
		insert(22L, 7L, "MEMBER", "ACTIVE", "NONE");
		insert(20L, 8L, "MEMBER", "PENDING", "NONE");

		List<InvalidatedApplication> result = inTransaction(() -> command.invalidatePendingWithoutPoints(7L))
				.orElseThrow();

		assertEquals(List.of(new InvalidatedApplication(mine20, 20L, 3L), new InvalidatedApplication(mine21, 21L, 4L)),
				result);
		assertEquals(2L, count("SELECT count(*) FROM morak.participation WHERE status = 'INVALIDATED' AND user_id = 7"));
		assertEquals("PENDING", jdbc.sql("SELECT status FROM morak.participation WHERE user_id = 8")
				.query(String.class).single());
		assertEquals("ACTIVE", jdbc.sql("SELECT status FROM morak.participation WHERE challenge_id = 22")
				.query(String.class).single());
	}

	@Test
	void changesNothingWhenAPendingApplicationHoldsPoints() {
		insert(20L, 3L, "HOST", "ACTIVE", "NONE");
		insert(21L, 4L, "HOST", "ACTIVE", "NONE");
		insert(20L, 7L, "MEMBER", "PENDING", "NONE");
		insert(21L, 7L, "MEMBER", "PENDING", "HELD");

		assertTrue(inTransaction(() -> command.invalidatePendingWithoutPoints(7L)).isEmpty());

		assertEquals(2L, count("SELECT count(*) FROM morak.participation WHERE status = 'PENDING' AND user_id = 7"));
	}

	private <T> T inTransaction(java.util.function.Supplier<T> action) {
		return new TransactionTemplate(transactionManager).execute(status -> action.get());
	}

	private void inTransaction(Runnable action) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
	}

	private long insert(long challengeId, long userId, String role, String status, String pointStatus) {
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, applied_at)
				VALUES (:challengeId, :userId, :role, :status, :pointStatus, :appliedAt)
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.param("role", role)
				.param("status", status)
				.param("pointStatus", pointStatus)
				.param("appliedAt", NOW_UTC.plusSeconds(userId))
				.query(Long.class)
				.single();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}
}

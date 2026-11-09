package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.domain.entity.Challenge.Category;
import com.morak.feature.challenge.domain.entity.Challenge.Visibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
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
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
		"morak.auth.jwt.secret=integration-test-secret-integration-test-secret",
		"morak.auth.refresh-token-cleanup.cron=-",
		"morak.auth.refresh-token-cleanup.retention=P7D"
})
class CreateChallengeTransactionIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {
		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}

		@Bean
		@Primary
		Clock challengeTestClock() {
			return Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private CreateChallengeTransaction transaction;
	@Autowired private PlatformTransactionManager transactionManager;

	@BeforeEach
	void clearFixtures() {
		jdbc.sql("""
				TRUNCATE morak.challenge, morak.participation, morak.notification, morak.chat_message
				""").update();
		jdbc.sql("""
				INSERT INTO morak.users (id, status, created_at, updated_at)
				VALUES (1, 'ACTIVE', now(), now()), (2, 'ACTIVE', now(), now())
				ON CONFLICT (id) DO NOTHING
				""").update();
	}

	@Test
	void createsRecruitingChallengeAndRegistersActiveHost() {
		long id = transaction.create(1, command(0), "000001");

		assertEquals("RECRUITING:1", jdbc.sql("""
				SELECT status || ':' || current_count FROM morak.challenge WHERE id = :id
				""").param("id", id).query(String.class).single());
		assertEquals("HOST:ACTIVE", jdbc.sql("""
				SELECT role || ':' || status FROM morak.participation
				""").query(String.class).single());
		assertEquals(0L, number("SELECT count(*) FROM morak.point_account"));
	}

	@Test
	void rejectsCreationThatWouldRequireChargingPoints() {
		BusinessException error = assertThrows(BusinessException.class,
				() -> transaction.create(1, command(1000), "000001"));

		assertEquals(ChallengeErrorCode.POINTS_NOT_SUPPORTED, error.getErrorCode());
		assertEquals(0L, number("SELECT count(*) FROM morak.challenge"));
		assertEquals(0L, number("SELECT count(*) FROM morak.participation"));
	}

	@Test
	void rollsBackChallengeAndHostTogether() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			transaction.create(1, command(0), "000001");
			status.setRollbackOnly();
		});

		assertEquals(0L, number("SELECT count(*) FROM morak.challenge"));
		assertEquals(0L, number("SELECT count(*) FROM morak.participation"));
	}

	@Test
	void onlyOneConcurrentCreationReachesTheLimitAndInvalidatesPendingApplication() throws Exception {
		long otherChallenge = createWithFourActiveChallenges();
		jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, status, applied_at)
				VALUES (:challengeId, 1, 'PENDING', now())
				""").param("challengeId", otherChallenge).update();

		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(creation("100005", start));
			var second = executor.submit(creation("100006", start));
			start.countDown();
			assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
		}

		assertEquals(5L, number("SELECT count(*) FROM morak.participation WHERE user_id = 1 AND status = 'ACTIVE'"));
		assertEquals(1L, number("SELECT count(*) FROM morak.participation WHERE status = 'INVALIDATED'"));
		assertEquals(1L, number("SELECT count(*) FROM morak.notification WHERE recipient_user_id = 2"));
	}

	@Test
	void rejectsFifthCreationWithoutChangingPendingApplicationThatRequiresPoints() {
		long otherChallenge = createWithFourActiveChallenges();
		jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, status, applied_at, deposit_amount, deposit_status)
				VALUES (:challengeId, 1, 'PENDING', now(), 1000, 'PAID')
				""").param("challengeId", otherChallenge).update();

		BusinessException error = assertThrows(BusinessException.class,
				() -> transaction.create(1, command(0), "100005"));

		assertEquals(ChallengeErrorCode.PENDING_POINTS_NOT_SUPPORTED, error.getErrorCode());
		assertEquals(3L, number("SELECT count(*) FROM morak.challenge WHERE owner_user_id = 1"));
		assertEquals(1L, number("SELECT count(*) FROM morak.participation WHERE status = 'PENDING'"));
		assertEquals(0L, number("SELECT count(*) FROM morak.notification"));
	}

	/** 개설한 세 방과 일반 참가자로 합류한 한 방을 함께 참여 한도에 포함시킨다. */
	private long createWithFourActiveChallenges() {
		long otherChallenge = transaction.create(2, command(0), "100000");
		long joinedChallenge = transaction.create(2, command(0), "100004");
		for (int index = 1; index <= 3; index++) {
			transaction.create(1, command(0), "10000" + index);
		}
		jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at, joined_at)
				VALUES (:challengeId, 1, 'MEMBER', 'ACTIVE', now(), now())
				""").param("challengeId", joinedChallenge).update();
		jdbc.sql("UPDATE morak.challenge SET current_count = 2 WHERE id = :challengeId")
				.param("challengeId", joinedChallenge).update();
		return otherChallenge;
	}

	private Callable<Boolean> creation(String code, CountDownLatch start) {
		return () -> {
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("개설 요청 시작 대기 시간 초과");
			}
			try {
				transaction.create(1, command(0), code);
				return true;
			} catch (BusinessException exception) {
				assertEquals(ChallengeErrorCode.PARTICIPATION_LIMIT, exception.getErrorCode());
				return false;
			}
		};
	}

	private long number(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private CreateChallengeCommand command(int amount) {
		LocalDate start = LocalDate.of(2026, 12, 1);
		return new CreateChallengeCommand("독서", Visibility.PUBLIC, Category.READING, "목적", "미션", "인증",
				start, start.plusDays(6), 3, amount, null);
	}
}

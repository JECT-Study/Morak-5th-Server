package com.morak.feature.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.notification.application.dto.NotificationContent.ParticipationApproved;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL(Testcontainers)에서 dedup_key 유니크와 ON CONFLICT DO NOTHING이 의도대로 동작하는지 확인한다.
 * Docker가 실행 중이어야 한다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class NotificationIntegrationTest {

	private static final String TITLE = "매일 아침 러닝";
	private static final int THREADS = 10;

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.notification").update();
	}

	@Test
	void sameDedupKeyCalledTwiceIsStoredOnce() {
		NotifyCommand command = command(10L, "PARTICIPATION_APPROVED:7");

		assertTrue(notificationService.notify(command));
		assertFalse(notificationService.notify(command));

		assertEquals(1, count());
	}

	@Test
	void concurrentCallsWithSameDedupKeyAreStoredOnce() throws Exception {
		NotifyCommand command = command(10L, "PARTICIPATION_APPROVED:7");

		List<Boolean> results = runConcurrently(() -> notificationService.notify(command));

		assertEquals(1, results.stream().filter(created -> created).count());
		assertEquals(1, count());
	}

	@Test
	void differentDedupKeysAreStoredSeparately() {
		notificationService.notify(command(10L, "KEY:1"));
		notificationService.notify(command(10L, "KEY:2"));

		assertEquals(2, count());
	}

	@Test
	void commandsWithoutDedupKeyAreAllStored() {
		notificationService.notify(command(10L, null));
		notificationService.notify(command(10L, null));

		assertEquals(2, count());
	}

	@Test
	void duplicateDoesNotBreakCallersTransaction() {
		notificationService.notify(command(10L, "KEY:1"));

		transactionTemplate.executeWithoutResult(status -> {
			assertFalse(notificationService.notify(command(10L, "KEY:1")));
			assertTrue(notificationService.notify(command(10L, "KEY:2")));
		});

		assertEquals(2, count());
	}

	@Test
	void notificationRollsBackWithCallersTransaction() {
		assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
			notificationService.notify(command(10L, "KEY:1"));
			throw new IllegalStateException("호출한 도메인 실패");
		}));

		assertEquals(0, count());
	}

	@Test
	void storesMessageTargetAndUtcCreatedAt() {
		notificationService.notify(new NotifyCommand(10L, new ParticipationApproved(TITLE), "CHALLENGE", 5L, "KEY:1"));

		var row = jdbc.sql("SELECT type, title, body, target_type, target_id, read_at FROM morak.notification")
				.query()
				.singleRow();
		assertEquals("PARTICIPATION_APPROVED", row.get("type"));
		assertEquals("참여 확정", row.get("title"));
		assertEquals("매일 아침 러닝 참여가 확정됐어요", row.get("body"));
		assertEquals("CHALLENGE", row.get("target_type"));
		assertEquals(5L, ((Number) row.get("target_id")).longValue());
		assertNull(row.get("read_at"));
	}

	private static NotifyCommand command(Long recipientUserId, String dedupKey) {
		return new NotifyCommand(recipientUserId, new ParticipationApproved(TITLE), null, null, dedupKey);
	}

	private long count() {
		return jdbc.sql("SELECT count(*) FROM morak.notification").query(Long.class).single();
	}

	private <T> List<T> runConcurrently(Callable<T> task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(THREADS);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get());
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}
}

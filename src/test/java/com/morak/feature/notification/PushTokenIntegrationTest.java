package com.morak.feature.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.morak.feature.notification.application.service.PushTokenService;
import java.util.ArrayList;
import java.util.List;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL(Testcontainers)에서 토큰 덮어쓰기(ON CONFLICT)와 본인 토큰만 삭제하는 쿼리를 확인한다.
 * Docker가 실행 중이어야 한다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class PushTokenIntegrationTest {

	private static final Long USER = 10L;
	private static final Long OTHER = 20L;
	private static final String TOKEN = "ExponentPushToken[device-1]";
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
	private PushTokenService service;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.push_token").update();
		jdbc.sql("DELETE FROM morak.push_subscription").update();
	}

	private long count(String table) {
		return jdbc.sql("SELECT count(*) FROM morak." + table).query(Long.class).single();
	}

	private Long ownerOf(String token) {
		return jdbc.sql("""
						SELECT s.user_id FROM morak.push_token t
						JOIN morak.push_subscription s ON s.id = t.subscription_id
						WHERE t.token = :token
						""")
				.param("token", token)
				.query(Long.class)
				.single();
	}

	@Test
	void registersTokenAndCreatesSubscriptionEnabledByDefault() {
		service.register(USER, TOKEN, "Expo/1.0");

		assertEquals(1, count("push_token"));
		assertEquals(1, count("push_subscription"));
		assertEquals(USER, ownerOf(TOKEN));
		assertEquals(
				"EXPO",
				jdbc.sql("SELECT channel FROM morak.push_token").query(String.class).single());
		assertEquals(
				true,
				jdbc.sql("SELECT enabled FROM morak.push_subscription").query(Boolean.class).single());
	}

	@Test
	void sameTokenRegisteredTwiceIsStoredOnce() {
		service.register(USER, TOKEN, null);
		service.register(USER, TOKEN, null);

		assertEquals(1, count("push_token"));
		assertEquals(1, count("push_subscription"));
	}

	@Test
	void sameTokenFromAnotherUserMovesToThatUser() {
		service.register(USER, TOKEN, null);

		service.register(OTHER, TOKEN, null);

		assertEquals(1, count("push_token"));
		assertEquals(OTHER, ownerOf(TOKEN));
	}

	@Test
	void oneUserWithTwoDevicesHasOneSubscriptionAndTwoTokens() {
		service.register(USER, "ExponentPushToken[device-1]", null);
		service.register(USER, "ExponentPushToken[device-2]", null);

		assertEquals(1, count("push_subscription"));
		assertEquals(2, count("push_token"));
	}

	@Test
	void concurrentRegistrationOfSameTokenAndUserStoresOnce() throws Exception {
		runConcurrently(() -> service.register(USER, TOKEN, null));

		assertEquals(1, count("push_token"));
		assertEquals(1, count("push_subscription"));
	}

	@Test
	void concurrentRegistrationOfSameTokenByDifferentUsersKeepsOneRowWithOneOwner() throws Exception {
		List<Runnable> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			Long user = i % 2 == 0 ? USER : OTHER;
			tasks.add(() -> service.register(user, TOKEN, null));
		}
		runAll(tasks);

		assertEquals(1, count("push_token"));
		assertEquals(2, count("push_subscription"));
	}

	@Test
	void unregisterDeletesOnlyOwnToken() {
		service.register(USER, TOKEN, null);
		service.register(OTHER, "ExponentPushToken[device-2]", null);

		service.unregister(OTHER, TOKEN);
		assertEquals(2, count("push_token"));

		service.unregister(USER, TOKEN);
		assertEquals(1, count("push_token"));
		assertEquals(OTHER, ownerOf("ExponentPushToken[device-2]"));
	}

	@Test
	void unregisterOfMissingTokenSucceedsAndKeepsSubscription() {
		service.register(USER, TOKEN, null);
		service.unregister(USER, "ExponentPushToken[never-registered]");

		assertEquals(1, count("push_token"));
		service.unregister(USER, TOKEN);
		service.unregister(USER, TOKEN);
		assertEquals(0, count("push_token"));
		assertEquals(1, count("push_subscription"));
	}

	private void runConcurrently(Runnable task) throws Exception {
		List<Runnable> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			tasks.add(task);
		}
		runAll(tasks);
	}

	private void runAll(List<Runnable> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (Runnable task : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					task.run();
					return null;
				}));
			}
			start.countDown();
			for (Future<?> future : futures) {
				future.get();
			}
		} finally {
			executor.shutdownNow();
		}
	}
}

package com.morak.feature.ad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.dto.result.AdRewardResult;
import com.morak.feature.ad.application.service.ClaimAdRewardService;
import com.morak.feature.ad.application.service.StartAdRewardService;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
 * 실제 PostgreSQL(Testcontainers)에서 하루 5회 한도와 1회용 토큰이 동시 요청에도 지켜지는지 확인한다.
 * Docker가 실행 중이어야 한다. 최소 시청 시간은 0으로 두어 발급 직후에도 완료할 수 있게 한다.
 */
@SpringBootTest(properties = {
		"morak.auth.jwt.secret=integration-test-secret-integration-test-secret",
		"morak.ad.mock-enabled=true",
		"morak.ad.token-secret=ad-integration-secret-ad-integration-secret",
		"morak.ad.min-watch=PT0S"
})
class AdRewardIntegrationTest {

	private static final long USER = 10L;
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
	private StartAdRewardService start;

	@Autowired
	private ClaimAdRewardService claim;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.point_transaction").update();
		jdbc.sql("DELETE FROM morak.point_account").update();
	}

	@Test
	void concurrentClaimsWithDifferentTokensNeverExceedDailyLimit() throws Exception {
		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			String token = start.start(USER).token();
			tasks.add(() -> claim.claim(USER, token));
		}

		List<Object> outcomes = runAll(tasks);

		assertEquals(5, outcomes.stream().filter(AdRewardResult.class::isInstance).count());
		assertEquals(THREADS - 5, outcomes.stream()
				.filter(outcome -> outcome instanceof BusinessException exception
						&& exception.getErrorCode() == AdErrorCode.DAILY_LIMIT_EXCEEDED)
				.count());
		assertEquals(2_500, balance());
		assertEquals(5, countRewards());
	}

	@Test
	void concurrentClaimsWithSameTokenGrantOnce() throws Exception {
		String token = start.start(USER).token();
		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			tasks.add(() -> claim.claim(USER, token));
		}

		List<Object> outcomes = runAll(tasks);

		assertEquals(THREADS, outcomes.stream().filter(AdRewardResult.class::isInstance).count());
		assertEquals(500, balance());
		assertEquals(1, countRewards());
	}

	@Test
	void sixthStartIsRejectedAfterFiveRewards() {
		for (int i = 0; i < 5; i++) {
			claim.claim(USER, start.start(USER).token());
		}

		BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
				BusinessException.class, () -> start.start(USER));

		assertEquals(AdErrorCode.DAILY_LIMIT_EXCEEDED, exception.getErrorCode());
	}

	private long balance() {
		return jdbc.sql("SELECT balance FROM morak.point_account WHERE user_id = :userId")
				.param("userId", USER)
				.query(Long.class)
				.single();
	}

	private long countRewards() {
		return jdbc.sql("SELECT count(*) FROM morak.point_transaction WHERE reason = 'AD_REWARD'")
				.query(Long.class)
				.single();
	}

	private List<Object> runAll(List<Callable<Object>> tasks) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch go = new CountDownLatch(1);
		try {
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> task : tasks) {
				futures.add(executor.submit(() -> {
					go.await();
					return task.call();
				}));
			}
			go.countDown();
			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> future : futures) {
				try {
					outcomes.add(future.get());
				} catch (ExecutionException exception) {
					outcomes.add(exception.getCause());
				}
			}
			return outcomes;
		} finally {
			executor.shutdownNow();
		}
	}
}

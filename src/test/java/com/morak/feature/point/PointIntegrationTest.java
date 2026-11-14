package com.morak.feature.point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.morak.common.exception.BusinessException;
import com.morak.feature.point.application.dto.PointChangeResult;
import com.morak.feature.point.application.service.PointService;
import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.point.domain.exception.PointErrorCode;
import com.morak.feature.point.domain.repository.PointTransactionRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL(Testcontainers)과 트랜잭션 프록시를 거쳐 계정 행 락, 조건부 UPDATE, 롤백을 확인한다.
 * Docker가 실행 중이어야 한다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class PointIntegrationTest {

	private static final Long USER = 10L;
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
	private PointService pointService;

	@Autowired
	private JdbcClient jdbc;

	@MockitoSpyBean
	private PointTransactionRepository transactionRepository;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.point_transaction").update();
		jdbc.sql("DELETE FROM morak.point_account").update();
	}

	@Test
	void concurrentRequestsWithSameKeyAreAppliedOnce() throws Exception {
		pointService.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");

		List<PointChangeResult> results = runConcurrently(() -> pointService.deposit(USER, 7L, TITLE, 3_000));

		assertEquals(1, results.stream().map(PointChangeResult::transactionId).distinct().count());
		assertEquals(7_000, balance());
		assertEquals(1, countLedger("DEPOSIT:7"));
	}

	@Test
	void concurrentDeductionsNeverOverdraw() throws Exception {
		pointService.grant(USER, PointGrantReason.SIGNUP_GIFT, 5_000, "SIGNUP_GIFT:10");
		List<Callable<PointChangeResult>> tasks = new ArrayList<>();
		for (long participation = 1; participation <= THREADS; participation++) {
			long id = participation;
			tasks.add(() -> pointService.deposit(USER, id, TITLE, 1_000));
		}

		List<Object> outcomes = runAll(tasks);

		long succeeded = outcomes.stream().filter(PointChangeResult.class::isInstance).count();
		long insufficient = outcomes.stream()
				.filter(outcome -> outcome instanceof BusinessException exception
						&& exception.getErrorCode() == PointErrorCode.INSUFFICIENT_BALANCE)
				.count();
		assertEquals(5, succeeded);
		assertEquals(THREADS - 5, insufficient);
		assertEquals(0, balance());
		assertEquals(6, jdbc.sql("SELECT count(*) FROM morak.point_transaction").query(Long.class).single());
	}

	@Test
	void ledgerSaveFailureRollsBackBalanceChange() {
		pointService.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");
		doThrow(new DataIntegrityViolationException("uk_point_transaction_idempotency"))
				.when(transactionRepository).save(any());

		BusinessException exception = assertThrows(
				BusinessException.class, () -> pointService.deposit(USER, 7L, TITLE, 3_000));

		assertEquals(PointErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
		assertEquals(10_000, balance());
		assertEquals(0, countLedger("DEPOSIT:7"));
	}

	@Test
	void failedDeductionDoesNotLeaveEmptyAccount() {
		assertThrows(BusinessException.class, () -> pointService.deposit(USER, 7L, TITLE, 3_000));

		assertEquals(0, jdbc.sql("SELECT count(*) FROM morak.point_account").query(Long.class).single());
	}

	private long balance() {
		return jdbc.sql("SELECT balance FROM morak.point_account WHERE user_id = :userId")
				.param("userId", USER)
				.query(Long.class)
				.single();
	}

	private long countLedger(String idempotencyKey) {
		return jdbc.sql("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key = :key")
				.param("key", idempotencyKey)
				.query(Long.class)
				.single();
	}

	/** 같은 작업을 THREADS개 스레드에서 동시에 실행한다. 하나라도 예외가 나면 실패한다. */
	private List<PointChangeResult> runConcurrently(Callable<PointChangeResult> task) throws Exception {
		List<PointChangeResult> results = new ArrayList<>();
		for (Object outcome : runAll(java.util.Collections.nCopies(THREADS, task))) {
			assertTrue(outcome instanceof PointChangeResult, () -> "unexpected outcome: " + outcome);
			results.add((PointChangeResult) outcome);
		}
		return results;
	}

	/** 모든 작업을 시작 신호에 맞춰 동시에 실행하고, 각 결과 또는 던진 예외를 돌려준다. */
	private List<Object> runAll(List<Callable<PointChangeResult>> tasks) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<PointChangeResult>> futures = new ArrayList<>();
			for (Callable<PointChangeResult> task : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<Object> outcomes = new ArrayList<>();
			for (Future<PointChangeResult> future : futures) {
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

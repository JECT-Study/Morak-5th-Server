package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.result.DiscardedChallenge;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.application.service.ApplyParticipationService;
import com.morak.feature.participation.application.service.DecideApplicationService;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.point.application.service.PointService;
import com.morak.feature.point.domain.entity.PointGrantReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 시작 보류 챌린지 폐기(위키 §4.2)를 실제 PostgreSQL(Testcontainers)과 실제 트랜잭션으로 검증한다. Docker가 실행 중이어야 한다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class DiscardChallengeIntegrationTest {

	private static final long HOST = 1L;
	private static final int POINT = 1_000;
	private static final AtomicInteger INVITE_SEQ = new AtomicInteger(500);

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private DiscardChallengeService discardService;
	@Autowired private ApplyParticipationService applyService;
	@Autowired private DecideApplicationService decideService;
	@MockitoSpyBean private ParticipationCommand participationCommand;
	@Autowired private PointService pointService;
	@Autowired private TransactionTemplate transactionTemplate;

	@BeforeEach
	void clear() {
		jdbc.sql("DELETE FROM morak.notification").update();
		jdbc.sql("DELETE FROM morak.chat_message").update();
		jdbc.sql("DELETE FROM morak.participation_result").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
		jdbc.sql("DELETE FROM morak.point_transaction").update();
		jdbc.sql("DELETE FROM morak.point_account").update();
	}

	@Test
	void discardRefundsEveryParticipantMarksThemDiscardedAndNotifiesEveryoneExceptHost() {
		long challenge = startHeldChallengeWithHostApprovedMemberAndPendingApplicant();

		DiscardedChallenge result = discardService.discard(HOST, challenge);

		assertEquals(new DiscardedChallenge(challenge, "DISCARDED"), result);
		assertEquals("DISCARDED", challengeStatus(challenge));
		for (long user : List.of(1L, 2L, 3L)) {
			assertEquals("DISCARDED", participationStatus(user));
			assertEquals("REFUNDED", pointStatus(user));
			assertEquals("DISCARDED", jdbc.sql("SELECT end_reason FROM morak.participation WHERE user_id = :u")
					.param("u", user).query(String.class).single());
			assertEquals(10_000, balance(user));
		}
		assertEquals(3, count("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key LIKE 'REFUND:%'"));
		assertEquals(0, count("SELECT count(*) FROM morak.participation_result"));
		assertEquals(List.of(), notificationTypes(1L));
		assertEquals(List.of("CHALLENGE_DISCARDED"), notificationTypes(2L));
		assertEquals(List.of("CHALLENGE_DISCARDED"), notificationTypes(3L));
		assertTrue(notificationBody(2L).contains("걸어 둔 1,000P는 전액 반환됐어요"));
	}

	@Test
	void discardingTwiceKeepsLedgerAndNotificationsUnchanged() {
		long challenge = startHeldChallengeWithHostApprovedMemberAndPendingApplicant();
		discardService.discard(HOST, challenge);
		long refunds = count("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key LIKE 'REFUND:%'");
		long notifications = count("SELECT count(*) FROM morak.notification WHERE type = 'CHALLENGE_DISCARDED'");

		DiscardedChallenge again = discardService.discard(HOST, challenge);

		assertEquals(new DiscardedChallenge(challenge, "DISCARDED"), again);
		assertEquals(refunds, count("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key LIKE 'REFUND:%'"));
		assertEquals(notifications, count("SELECT count(*) FROM morak.notification WHERE type = 'CHALLENGE_DISCARDED'"));
		assertEquals(10_000, balance(2L));
	}

	@Test
	void applicationsChangedOnFirstAttemptIsRetriedInNewTransaction() {
		long challenge = startHeldChallengeWithHostApprovedMemberAndPendingApplicant();
		// 첫 시도에서 새 신청이 끼어든 것처럼 거절하고, 두 번째 시도는 실제로 처리한다.
		// 트랜잭션 프록시를 거치지 않도록 스파이 본체에 스텁을 건다.
		ParticipationCommand spy = AopTestUtils.getTargetObject(participationCommand);
		doThrow(new BusinessException(ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED)).doCallRealMethod()
				.when(spy).discardAll(anyLong(), anyString(), any(), any());

		DiscardedChallenge result = discardService.discard(HOST, challenge);

		assertEquals("DISCARDED", result.status());
		assertEquals("DISCARDED", challengeStatus(challenge));
		verify(spy, times(2)).lockPointAccountsForDiscard(challenge);
		assertEquals(3, count("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key LIKE 'REFUND:%'"));
		assertEquals(2, count("SELECT count(*) FROM morak.notification WHERE type = 'CHALLENGE_DISCARDED'"));
		assertEquals(10_000, balance(3L));
	}

	@Test
	void concurrentDiscardsRefundOnlyOnce() throws Exception {
		long challenge = startHeldChallengeWithHostApprovedMemberAndPendingApplicant();
		int threads = 4;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<DiscardedChallenge>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return discardService.discard(HOST, challenge);
				}));
			}
			start.countDown();
			for (Future<DiscardedChallenge> future : futures) {
				assertEquals("DISCARDED", future.get(30, TimeUnit.SECONDS).status());
			}
		} finally {
			// 단언이 실패하거나 작업이 예외를 던져도 스레드가 남지 않게 한다.
			pool.shutdownNow();
			assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
		}

		assertEquals(3, count("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key LIKE 'REFUND:%'"));
		assertEquals(2, count("SELECT count(*) FROM morak.notification WHERE type = 'CHALLENGE_DISCARDED'"));
		assertEquals(10_000, balance(1L));
		assertEquals(10_000, balance(2L));
		assertEquals(10_000, balance(3L));
	}

	@Test
	void nonOwnerCannotDiscardAndNothingChanges() {
		long challenge = startHeldChallengeWithHostApprovedMemberAndPendingApplicant();

		BusinessException error = assertThrows(BusinessException.class, () -> discardService.discard(2L, challenge));

		assertEquals(ChallengeErrorCode.NOT_OWNER, error.getErrorCode());
		assertEquals("START_HELD", challengeStatus(challenge));
		assertEquals("ACTIVE", participationStatus(2L));
		assertEquals(9_000, balance(2L));
	}

	@Test
	void challengeThatIsNotStartHeldCannotBeDiscarded() {
		long challenge = startHeldChallengeWithHostApprovedMemberAndPendingApplicant();
		for (String status : List.of("RECRUITING", "IN_PROGRESS", "FINALIZING", "ENDED")) {
			setChallengeStatus(challenge, status);

			BusinessException error = assertThrows(BusinessException.class, () -> discardService.discard(HOST, challenge));

			assertEquals(ChallengeErrorCode.NOT_START_HELD, error.getErrorCode());
			assertEquals(status, challengeStatus(challenge));
		}
		assertEquals("ACTIVE", participationStatus(2L));
		assertEquals("HELD", pointStatus(3L));
		assertEquals(9_000, balance(3L));
		assertEquals(0, count("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key LIKE 'REFUND:%'"));
	}

	@Test
	void applicantWhoseAccountWasNotLockedRollsBackWholeDiscard() {
		long challenge = startHeldChallengeWithHostApprovedMemberAndPendingApplicant();

		// 계정을 잠그지 못한 포인트 보유자가 있으면(그 사이 새 지원) 아무것도 바꾸지 않는다.
		BusinessException error = assertThrows(BusinessException.class, () -> transactionTemplate.executeWithoutResult(
				status -> participationCommand.discardAll(challenge, "아침 독서", Set.of(1L, 2L), Instant.now())));

		assertEquals(ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED, error.getErrorCode());
		assertEquals("ACTIVE", participationStatus(2L));
		assertEquals("PENDING", participationStatus(3L));
		assertEquals("HELD", pointStatus(3L));
		assertEquals(9_000, balance(3L));
		assertEquals(0, count("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key LIKE 'REFUND:%'"));
	}

	@Test
	void discardWithoutPointsDiscardsEveryoneAndStillNotifies() {
		long challenge = challenge(0);
		participation(challenge, HOST, "HOST", "ACTIVE", 0);
		participation(challenge, 2L, "MEMBER", "ACTIVE", 0);
		participation(challenge, 3L, "MEMBER", "PENDING", 0);
		participation(challenge, 4L, "MEMBER", "REJECTED", 0);
		setChallengeStatus(challenge, "START_HELD");

		discardService.discard(HOST, challenge);

		assertEquals("DISCARDED", participationStatus(2L));
		assertEquals("DISCARDED", participationStatus(3L));
		assertEquals("REJECTED", participationStatus(4L));
		assertEquals(List.of("CHALLENGE_DISCARDED"), notificationTypes(2L));
		assertEquals(List.of("CHALLENGE_DISCARDED"), notificationTypes(3L));
		assertEquals(List.of(), notificationTypes(4L));
		assertTrue(!notificationBody(2L).contains("반환"));
	}

	// ---- 준비·확인용 ----

	/** 방장(1, 포인트 건 상태), 승인된 팀원(2), 승인 대기 신청자(3). 모두 1,000P를 걸었고 잔액은 9,000P다. */
	private long startHeldChallengeWithHostApprovedMemberAndPendingApplicant() {
		long challenge = challenge(POINT);
		pointService.grant(HOST, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:" + HOST);
		long hostParticipation = participation(challenge, HOST, "HOST", "ACTIVE", POINT);
		pointService.deposit(HOST, hostParticipation, "아침 독서", POINT);
		for (long user : new long[] {2L, 3L}) {
			pointService.grant(user, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:" + user);
		}
		long approved = applyService.apply(2L, challenge, "각오", null);
		applyService.apply(3L, challenge, "각오", null);
		decideService.approve(HOST, challenge, approved);
		setChallengeStatus(challenge, "START_HELD");
		assertEquals(9_000, balance(1L));
		assertEquals(9_000, balance(2L));
		assertEquals(9_000, balance(3L));
		return challenge;
	}

	private long challenge(int pointAmount) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, status, start_date, end_date,
											duration_weeks, capacity, current_count, point_amount, created_at, updated_at)
				VALUES (:owner, '아침 독서', 'STUDY', :code, 'RECRUITING', DATE '2026-12-01', DATE '2026-12-14', 2,
						5, 1, :point, now(), now())
				RETURNING id
				""")
				.param("owner", HOST)
				.param("code", String.format("%06d", INVITE_SEQ.incrementAndGet()))
				.param("point", pointAmount)
				.query(Long.class)
				.single();
	}

	private long participation(long challenge, long userId, String role, String status, int pointAmount) {
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, deposit_amount,
												applied_at, joined_at)
				VALUES (:challenge, :user, :role, :status, :pointStatus, :point, now(), now())
				RETURNING id
				""")
				.param("challenge", challenge)
				.param("user", userId)
				.param("role", role)
				.param("status", status)
				.param("pointStatus", pointAmount > 0 ? "HELD" : "NONE")
				.param("point", pointAmount)
				.query(Long.class)
				.single();
	}

	private void setChallengeStatus(long challenge, String status) {
		jdbc.sql("UPDATE morak.challenge SET status = :s WHERE id = :id").param("s", status).param("id", challenge).update();
	}

	private String challengeStatus(long challenge) {
		return jdbc.sql("SELECT status FROM morak.challenge WHERE id = :id").param("id", challenge)
				.query(String.class).single();
	}

	private String participationStatus(long userId) {
		return jdbc.sql("SELECT status FROM morak.participation WHERE user_id = :u").param("u", userId)
				.query(String.class).single();
	}

	private String pointStatus(long userId) {
		return jdbc.sql("SELECT point_status FROM morak.participation WHERE user_id = :u").param("u", userId)
				.query(String.class).single();
	}

	private long balance(long userId) {
		return pointService.getLedger(userId, null, 1).balance();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private List<String> notificationTypes(long userId) {
		return jdbc.sql("SELECT type FROM morak.notification WHERE recipient_user_id = :u AND type = 'CHALLENGE_DISCARDED' ORDER BY id")
				.param("u", userId).query(String.class).list();
	}

	private String notificationBody(long userId) {
		return jdbc.sql("SELECT body FROM morak.notification WHERE recipient_user_id = :u AND type = 'CHALLENGE_DISCARDED' ORDER BY id LIMIT 1")
				.param("u", userId).query(String.class).single();
	}
}

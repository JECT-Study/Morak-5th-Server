package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.reset;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.command.RescheduleChallengeCommand;
import com.morak.feature.challenge.application.dto.result.RescheduledChallenge;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import com.morak.feature.participation.application.service.ApplyParticipationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 시작 보류 기간 재설정(위키 §4.2, D-27)을 실제 PostgreSQL(Testcontainers)과 실제 트랜잭션으로 검증한다. Docker가 실행 중이어야 한다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class RescheduleChallengeIntegrationTest {

	private static final long HOST = 1L;
	private static final LocalDate NEW_START = LocalDate.of(2030, 1, 10);
	private static final LocalDate NEW_END = LocalDate.of(2030, 1, 16);
	private static final AtomicInteger INVITE_SEQ = new AtomicInteger(700);

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private RescheduleChallengeService rescheduleService;
	@Autowired private ApplyParticipationService applyService;
	@MockitoSpyBean private NotificationService notificationService;

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
	void rescheduleSavesPeriodReturnsToRecruitingAndNotifiesEveryoneExceptHost() {
		long challenge = startHeldChallengeWithHostMemberAndPendingApplicant();

		RescheduledChallenge result = rescheduleService.reschedule(HOST, challenge, command());

		assertEquals(new RescheduledChallenge(challenge, "RECRUITING", NEW_START, NEW_END), result);
		assertEquals("RECRUITING", challengeStatus(challenge));
		assertEquals(NEW_START, challengeDate(challenge, "start_date"));
		assertEquals(NEW_END, challengeDate(challenge, "end_date"));
		assertEquals(2, count("SELECT current_count FROM morak.challenge WHERE id = " + challenge));
		assertEquals(List.of(), notificationTypes(1L));
		assertEquals(List.of("SCHEDULE_RESET"), notificationTypes(2L));
		assertEquals(List.of("SCHEDULE_RESET"), notificationTypes(3L));
		assertTrue(notificationBody(2L).contains("1월 10일부터 1월 16일까지"));
		assertEquals("ACTIVE", participationStatus(2L));
		assertEquals("PENDING", participationStatus(3L));
	}

	@Test
	void sameRequestAgainIsRejectedAndNotificationsStayUnchanged() {
		long challenge = startHeldChallengeWithHostMemberAndPendingApplicant();
		rescheduleService.reschedule(HOST, challenge, command());

		BusinessException error =
				assertThrows(BusinessException.class, () -> rescheduleService.reschedule(HOST, challenge, command()));

		assertEquals(ChallengeErrorCode.NOT_START_HELD, error.getErrorCode());
		assertEquals("RECRUITING", challengeStatus(challenge));
		assertEquals(2, count("SELECT count(*) FROM morak.notification WHERE type = 'SCHEDULE_RESET'"));
	}

	@Test
	void concurrentSameRequestsSucceedOnceAndNotifyOnlyOnce() throws Exception {
		long challenge = startHeldChallengeWithHostMemberAndPendingApplicant();
		int threads = 4;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<String>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					try {
						return rescheduleService.reschedule(HOST, challenge, command()).status();
					} catch (BusinessException exception) {
						return exception.getErrorCode().code();
					}
				}));
			}
			start.countDown();
			List<String> outcomes = new ArrayList<>();
			for (Future<String> future : futures) {
				outcomes.add(future.get(30, TimeUnit.SECONDS));
			}
			// 챌린지 잠금으로 한 요청만 재설정하고 나머지는 이미 모집 중이라 409로 거절된다.
			assertEquals(1, outcomes.stream().filter("RECRUITING"::equals).count());
			assertEquals(threads - 1, outcomes.stream().filter("CHALLENGE_NOT_START_HELD"::equals).count());
		} finally {
			// 단언이 실패하거나 작업이 예외를 던져도 스레드가 남지 않게 한다.
			pool.shutdownNow();
			assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
		}

		assertEquals(2, count("SELECT count(*) FROM morak.notification WHERE type = 'SCHEDULE_RESET'"));
	}

	@Test
	void notificationFailureMidwayRollsBackRescheduleAndRetrySucceeds() {
		long challenge = startHeldChallengeWithHostMemberAndPendingApplicant();
		// 첫 수신자의 알림은 저장하고, 두 번째 수신자의 알림에서 실패시킨다.
		doCallRealMethod().doThrow(new IllegalStateException("알림 저장 실패"))
				.when(notificationService).notify(any(NotifyCommand.class));

		assertThrows(IllegalStateException.class, () -> rescheduleService.reschedule(HOST, challenge, command()));

		assertEquals("START_HELD", challengeStatus(challenge));
		assertEquals(LocalDate.of(2026, 11, 16), challengeDate(challenge, "start_date"));
		assertEquals(LocalDate.of(2026, 11, 29), challengeDate(challenge, "end_date"));
		assertEquals(0, count("SELECT count(*) FROM morak.notification"));

		reset(notificationService);
		RescheduledChallenge retried = rescheduleService.reschedule(HOST, challenge, command());

		assertEquals(new RescheduledChallenge(challenge, "RECRUITING", NEW_START, NEW_END), retried);
		assertEquals(NEW_START, challengeDate(challenge, "start_date"));
		assertEquals(List.of("SCHEDULE_RESET"), notificationTypes(2L));
		assertEquals(List.of("SCHEDULE_RESET"), notificationTypes(3L));
		assertEquals(2, count("SELECT count(*) FROM morak.notification"));
	}

	@Test
	void nonOwnerCannotRescheduleAndNothingChanges() {
		long challenge = startHeldChallengeWithHostMemberAndPendingApplicant();

		BusinessException error = assertThrows(BusinessException.class,
				() -> rescheduleService.reschedule(2L, challenge, command()));

		assertEquals(ChallengeErrorCode.NOT_OWNER, error.getErrorCode());
		assertEquals("START_HELD", challengeStatus(challenge));
		assertEquals(0, count("SELECT count(*) FROM morak.notification"));
	}

	@Test
	void challengeThatIsNotStartHeldCannotBeRescheduled() {
		long challenge = startHeldChallengeWithHostMemberAndPendingApplicant();
		for (String status : List.of("IN_PROGRESS", "FINALIZING", "ENDED", "DISCARDED")) {
			setChallengeStatus(challenge, status);

			BusinessException error = assertThrows(BusinessException.class,
					() -> rescheduleService.reschedule(HOST, challenge, command()));

			assertEquals(ChallengeErrorCode.NOT_START_HELD, error.getErrorCode());
			assertEquals(status, challengeStatus(challenge));
		}
		assertEquals(0, count("SELECT count(*) FROM morak.notification"));
	}

	@Test
	void applicantCanApplyRightAfterReschedule() {
		long challenge = startHeldChallengeWithHostMemberAndPendingApplicant();
		rescheduleService.reschedule(HOST, challenge, command());

		long participation = applyService.apply(4L, challenge, "각오", null);

		assertTrue(participation > 0);
		assertEquals("PENDING", participationStatus(4L));
	}

	// ---- 준비·확인용 ----

	private static RescheduleChallengeCommand command() {
		return new RescheduleChallengeCommand(NEW_START, NEW_END);
	}

	/** 포인트 없는 챌린지: 방장(1), 승인된 팀원(2), 승인 대기 신청자(3), 거절된 신청자(5)는 알림 대상이 아니다. */
	private long startHeldChallengeWithHostMemberAndPendingApplicant() {
		long challenge = jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, status, start_date, end_date,
											duration_weeks, capacity, current_count, point_amount, created_at, updated_at)
				VALUES (:owner, '아침 독서', 'STUDY', :code, 'START_HELD', DATE '2026-11-16', DATE '2026-11-29', 2,
						5, 2, 0, now(), now())
				RETURNING id
				""")
				.param("owner", HOST)
				.param("code", String.format("%06d", INVITE_SEQ.incrementAndGet()))
				.query(Long.class)
				.single();
		participation(challenge, HOST, "HOST", "ACTIVE");
		participation(challenge, 2L, "DEPUTY", "ACTIVE");
		participation(challenge, 3L, "MEMBER", "PENDING");
		participation(challenge, 5L, "MEMBER", "REJECTED");
		return challenge;
	}

	private void participation(long challenge, long userId, String role, String status) {
		jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, deposit_amount,
												applied_at, joined_at)
				VALUES (:challenge, :user, :role, :status, 'NONE', 0, now(), now())
				""")
				.param("challenge", challenge)
				.param("user", userId)
				.param("role", role)
				.param("status", status)
				.update();
	}

	private void setChallengeStatus(long challenge, String status) {
		jdbc.sql("UPDATE morak.challenge SET status = :s WHERE id = :id").param("s", status).param("id", challenge).update();
	}

	private String challengeStatus(long challenge) {
		return jdbc.sql("SELECT status FROM morak.challenge WHERE id = :id").param("id", challenge)
				.query(String.class).single();
	}

	private LocalDate challengeDate(long challenge, String column) {
		return jdbc.sql("SELECT " + column + " FROM morak.challenge WHERE id = :id").param("id", challenge)
				.query(LocalDate.class).single();
	}

	private String participationStatus(long userId) {
		return jdbc.sql("SELECT status FROM morak.participation WHERE user_id = :u").param("u", userId)
				.query(String.class).single();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private List<String> notificationTypes(long userId) {
		return jdbc.sql("SELECT type FROM morak.notification WHERE recipient_user_id = :u AND type = 'SCHEDULE_RESET' ORDER BY id")
				.param("u", userId).query(String.class).list();
	}

	private String notificationBody(long userId) {
		return jdbc.sql("SELECT body FROM morak.notification WHERE recipient_user_id = :u AND type = 'SCHEDULE_RESET' ORDER BY id LIMIT 1")
				.param("u", userId).query(String.class).single();
	}
}

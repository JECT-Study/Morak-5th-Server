package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.application.command.ParticipationCommand.RoleHandOver;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.Application;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.point.application.service.PointService;
import com.morak.feature.point.domain.entity.PointGrantReason;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 지원 → 승인 → 자동 거절·환급·알림, 방장·부방장 승계, 무효 신청 확인을 실제 PostgreSQL(Testcontainers)과 실제 트랜잭션으로
 * 검증한다. Docker가 실행 중이어야 한다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class ApplicationFlowIntegrationTest {

	private static final long HOST = 1L;
	private static final long POINT = 1_000L;
	private static final AtomicInteger INVITE_SEQ = new AtomicInteger();

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private ApplyParticipationService applyService;
	@Autowired private DecideApplicationService decideService;
	@Autowired private ParticipationCommand participationCommand;
	@Autowired private ParticipationQuery participationQuery;
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

	// ---- 정원이 차면 남은 대기 신청 자동 거절 (#2) + 첫 합류자 부방장 (#8) ----

	@Test
	void approvalThatFillsChallengeRejectsTheRestRefundsPointsAndNotifiesEveryone() {
		long challenge = challenge(3, POINT);
		long host = participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		Applicants applicants = applyAll(challenge, 2L, 3L, 4L, 5L);

		decideService.approve(HOST, challenge, applicants.idOf(2L));
		decideService.approve(HOST, challenge, applicants.idOf(3L));

		assertEquals("DEPUTY:ACTIVE", roleAndStatus(2L));
		assertEquals("MEMBER:ACTIVE", roleAndStatus(3L));
		assertEquals("MEMBER:REJECTED", roleAndStatus(4L));
		assertEquals("MEMBER:REJECTED", roleAndStatus(5L));
		assertEquals(3, jdbc.sql("SELECT current_count FROM morak.challenge WHERE id = :id")
				.param("id", challenge).query(Integer.class).single());
		assertEquals("REFUNDED", pointStatus(4L));
		assertEquals("REFUNDED", pointStatus(5L));
		assertEquals("HELD", pointStatus(2L));
		assertEquals(10_000, balance(4L));
		assertEquals(10_000, balance(5L));
		assertEquals(9_000, balance(2L));
		assertEquals(9_000, balance(3L));
		assertEquals(List.of("PARTICIPATION_REQUESTED", "PARTICIPATION_REQUESTED", "PARTICIPATION_REQUESTED",
				"PARTICIPATION_REQUESTED"), notificationTypes(HOST));
		assertEquals(List.of("PARTICIPATION_APPROVED", "DEPUTY_ASSIGNED"), notificationTypes(2L));
		assertEquals(List.of("PARTICIPATION_APPROVED"), notificationTypes(3L));
		assertEquals(List.of("PARTICIPATION_REJECTED"), notificationTypes(4L));
		assertEquals(List.of("PARTICIPATION_REJECTED"), notificationTypes(5L));
		assertTrue(notificationBody(4L).contains("걸어 둔 1,000P는 전액 반환됐어요"));
		assertEquals(host, jdbc.sql("SELECT id FROM morak.participation WHERE user_id = 1")
				.query(Long.class).single());
	}

	@Test
	void applicationsWithoutPointsAreAlsoRejectedWithoutRefundSentence() {
		long challenge = challenge(2, 0);
		participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		Applicants applicants = applyAll(challenge, 2L, 3L);

		decideService.approve(HOST, challenge, applicants.idOf(2L));

		assertEquals("MEMBER:REJECTED", roleAndStatus(3L));
		assertEquals(List.of("PARTICIPATION_REJECTED"), notificationTypes(3L));
		assertFalse(notificationBody(3L).contains("반환"));
	}

	@Test
	void approvalThatDoesNotFillChallengeKeepsOthersPendingWithPointsHeld() {
		long challenge = challenge(5, POINT);
		participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		Applicants applicants = applyAll(challenge, 2L, 3L);

		decideService.approve(HOST, challenge, applicants.idOf(2L));

		assertEquals("MEMBER:PENDING", roleAndStatus(3L));
		assertEquals("HELD", pointStatus(3L));
		assertEquals(9_000, balance(3L));
		assertEquals(List.of(), notificationTypes(3L));
	}

	@Test
	void secondMemberDoesNotBecomeDeputyAgain() {
		long challenge = challenge(5, 0);
		participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		Applicants applicants = applyAll(challenge, 2L, 3L);

		decideService.approve(HOST, challenge, applicants.idOf(2L));
		decideService.approve(HOST, challenge, applicants.idOf(3L));

		assertEquals("DEPUTY:ACTIVE", roleAndStatus(2L));
		assertEquals("MEMBER:ACTIVE", roleAndStatus(3L));
		assertEquals(List.of("PARTICIPATION_APPROVED"), notificationTypes(3L));
	}

	@Test
	void failedApprovalRollsBackDeputyAssignmentAndAutoRejection() {
		long challenge = challenge(2, POINT);
		participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		Applicants applicants = applyAll(challenge, 2L, 3L);
		jdbc.sql("UPDATE morak.challenge SET status = 'IN_PROGRESS' WHERE id = :id").param("id", challenge).update();

		BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
				BusinessException.class, () -> decideService.approve(HOST, challenge, applicants.idOf(2L)));

		assertEquals(ParticipationErrorCode.CHALLENGE_NOT_RECRUITING, error.getErrorCode());
		assertEquals("MEMBER:PENDING", roleAndStatus(2L));
		assertEquals("MEMBER:PENDING", roleAndStatus(3L));
		assertEquals(9_000, balance(3L));
		// 신청 때 방장에게 간 #13은 남고, 승인 트랜잭션이 만든 알림(#1·#2·#8)은 롤백돼 없어야 한다.
		assertEquals(List.of("PARTICIPATION_REQUESTED", "PARTICIPATION_REQUESTED"), notificationTypes(HOST));
		assertEquals(0L, jdbc.sql("SELECT count(*) FROM morak.notification WHERE recipient_user_id <> :host")
				.param("host", HOST).query(Long.class).single());
	}

	@Test
	void concurrentApprovalsNeverDeadlockAndEndWithConsistentState() throws Exception {
		long challenge = challenge(3, POINT);
		participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		Applicants applicants = applyAll(challenge, 2L, 3L, 4L, 5L);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Void>> futures = new ArrayList<>();
		for (long user : new long[] {2L, 3L}) {
			futures.add(executor.submit(() -> {
				start.await();
				approveWithRetry(challenge, applicants.idOf(user));
				return null;
			}));
		}
		start.countDown();
		for (Future<Void> future : futures) {
			future.get();
		}
		executor.shutdownNow();

		assertEquals("ACTIVE", status(2L));
		assertEquals("ACTIVE", status(3L));
		assertEquals("REJECTED", status(4L));
		assertEquals("REJECTED", status(5L));
		assertEquals(3, jdbc.sql("SELECT current_count FROM morak.challenge WHERE id = :id")
				.param("id", challenge).query(Integer.class).single());
		assertEquals(10_000, balance(4L));
		assertEquals(10_000, balance(5L));
		assertEquals(1L, jdbc.sql("SELECT count(*) FROM morak.participation WHERE role = 'DEPUTY'")
				.query(Long.class).single());
	}

	/** 정원이 차는 순간과 겹쳐 승인 대기 신청이 바뀌었다는 409는 다시 시도하면 된다. 그 밖의 오류(교착 등)는 그대로 실패시킨다. */
	private void approveWithRetry(long challenge, long participationId) {
		for (int attempt = 0; attempt < 5; attempt++) {
			try {
				decideService.approve(HOST, challenge, participationId);
				return;
			} catch (BusinessException error) {
				if (error.getErrorCode() != ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED) {
					throw error;
				}
			}
		}
		throw new IllegalStateException("재시도해도 승인하지 못했습니다");
	}

	// ---- 무효 신청 확인 ----

	@Test
	void confirmingInvalidApplicationRemovesItFromPendingList() {
		long challenge = challenge(5, 0);
		participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		long invalidated = participationRow(challenge, 2L, "MEMBER", "INVALIDATED", -1);
		long pending = participationRow(challenge, 3L, "MEMBER", "PENDING", -1);

		List<Application> before = participationQuery.findPendingApplications(challenge);
		assertEquals(List.of(invalidated, pending), before.stream().map(Application::participationId).toList());
		assertTrue(before.get(0).invalidated());
		assertFalse(before.get(1).invalidated());

		decideService.confirmInvalidation(HOST, challenge, invalidated);
		decideService.confirmInvalidation(HOST, challenge, invalidated);

		List<Application> after = participationQuery.findPendingApplications(challenge);
		assertEquals(List.of(pending), after.stream().map(Application::participationId).toList());
		assertNotNull(jdbc.sql("SELECT invalid_confirmed_at FROM morak.participation WHERE id = :id")
				.param("id", invalidated).query(LocalDateTime.class).single());
		assertEquals("INVALIDATED", status(2L));
	}

	@Test
	void invalidApplicationCannotBeApprovedBeforeOrAfterConfirmation() {
		long challenge = challenge(5, 0);
		participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		long invalidated = participationRow(challenge, 2L, "MEMBER", "INVALIDATED", -1);

		BusinessException before = org.junit.jupiter.api.Assertions.assertThrows(
				BusinessException.class, () -> decideService.approve(HOST, challenge, invalidated));
		decideService.confirmInvalidation(HOST, challenge, invalidated);
		BusinessException after = org.junit.jupiter.api.Assertions.assertThrows(
				BusinessException.class, () -> decideService.approve(HOST, challenge, invalidated));

		assertEquals(ParticipationErrorCode.INVALID_STATUS_TRANSITION, before.getErrorCode());
		assertEquals(ParticipationErrorCode.INVALID_STATUS_TRANSITION, after.getErrorCode());
	}

	// ---- 방장·부방장 승계 ----

	@Test
	void hostLeavingPromotesDeputyAndEarliestMemberAndChangesChallengeOwner() {
		long challenge = challenge(5, 0);
		long host = participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		participationRow(challenge, 2L, "DEPUTY", "ACTIVE", 1);
		participationRow(challenge, 3L, "MEMBER", "ACTIVE", 2);
		participationRow(challenge, 4L, "MEMBER", "ACTIVE", 3);

		RoleHandOver result = transactionTemplate.execute(status -> participationCommand.handOverRoles(challenge, host));

		assertEquals(new RoleHandOver(2L, 3L, false), result);
		assertEquals("MEMBER", role(1L));
		assertEquals("HOST", role(2L));
		assertEquals("DEPUTY", role(3L));
		assertEquals("MEMBER", role(4L));
		assertEquals(2L, jdbc.sql("SELECT owner_user_id FROM morak.challenge WHERE id = :id")
				.param("id", challenge).query(Long.class).single());
	}

	@Test
	void deputyLeavingPromotesEarliestMemberWithoutChangingOwner() {
		long challenge = challenge(5, 0);
		participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		long deputy = participationRow(challenge, 2L, "DEPUTY", "ACTIVE", 1);
		participationRow(challenge, 3L, "MEMBER", "ACTIVE", 2);

		RoleHandOver result = transactionTemplate.execute(status -> participationCommand.handOverRoles(challenge, deputy));

		assertEquals(new RoleHandOver(null, 3L, false), result);
		assertEquals("MEMBER", role(2L));
		assertEquals("DEPUTY", role(3L));
		assertEquals("HOST", role(1L));
		assertEquals(HOST, jdbc.sql("SELECT owner_user_id FROM morak.challenge WHERE id = :id")
				.param("id", challenge).query(Long.class).single());
	}

	@Test
	void hostLeavingWithoutDeputyChangesNothing() {
		long challenge = challenge(5, 0);
		long host = participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		participationRow(challenge, 3L, "MEMBER", "ACTIVE", 2);

		RoleHandOver result = transactionTemplate.execute(status -> participationCommand.handOverRoles(challenge, host));

		assertEquals(new RoleHandOver(null, null, true), result);
		assertEquals("HOST", role(1L));
		assertEquals("MEMBER", role(3L));
		assertEquals(HOST, jdbc.sql("SELECT owner_user_id FROM morak.challenge WHERE id = :id")
				.param("id", challenge).query(Long.class).single());
	}

	@Test
	void handOverRollsBackWithCallerTransaction() {
		long challenge = challenge(5, 0);
		long host = participationRow(challenge, HOST, "HOST", "ACTIVE", 0);
		participationRow(challenge, 2L, "DEPUTY", "ACTIVE", 1);

		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> transactionTemplate.executeWithoutResult(status -> {
					participationCommand.handOverRoles(challenge, host);
					throw new IllegalStateException("퇴장 처리 실패");
				}));

		assertEquals("HOST", role(1L));
		assertEquals("DEPUTY", role(2L));
		assertEquals(HOST, jdbc.sql("SELECT owner_user_id FROM morak.challenge WHERE id = :id")
				.param("id", challenge).query(Long.class).single());
	}

	// ---- 준비·확인용 ----

	private record Applicants(java.util.Map<Long, Long> ids) {
		long idOf(long userId) {
			return ids.get(userId);
		}
	}

	/** 사용자마다 포인트 10,000P를 지급하고 지원시킨다. 지원 순서가 곧 신청 순서다. */
	private Applicants applyAll(long challenge, long... userIds) {
		java.util.Map<Long, Long> ids = new java.util.LinkedHashMap<>();
		for (long userId : userIds) {
			pointService.grant(userId, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:" + userId);
			ids.put(userId, applyService.apply(userId, challenge, "각오", null));
		}
		return new Applicants(ids);
	}

	private long challenge(int capacity, long pointAmount) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, status, start_date, end_date,
											duration_weeks, capacity, current_count, point_amount, created_at, updated_at)
				VALUES (:owner, '아침 독서', 'STUDY', :code, 'RECRUITING', DATE '2026-12-01', DATE '2026-12-14', 2,
						:capacity, 1, :point, now(), now())
				RETURNING id
				""")
				.param("owner", HOST)
				.param("code", String.format("%06d", INVITE_SEQ.incrementAndGet()))
				.param("capacity", capacity)
				.param("point", pointAmount)
				.query(Long.class)
				.single();
	}

	/** joinedOrder가 0 이상이면 그 순서로 합류한 것으로 넣고, 음수면 합류 시각이 없다(대기·무효 신청). */
	private long participationRow(long challenge, long userId, String role, String status, int joinedOrder) {
		LocalDateTime base = LocalDateTime.of(2026, 11, 18, 0, 0);
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at, joined_at)
				VALUES (:challenge, :user, :role, :status, :appliedAt, :joinedAt)
				RETURNING id
				""")
				.param("challenge", challenge)
				.param("user", userId)
				.param("role", role)
				.param("status", status)
				.param("appliedAt", base.plusMinutes(Math.max(joinedOrder, 0) + userId))
				.param("joinedAt", joinedOrder < 0 ? null : base.plusMinutes(joinedOrder))
				.query(Long.class)
				.single();
	}

	private String roleAndStatus(long userId) {
		return role(userId) + ":" + status(userId);
	}

	private String role(long userId) {
		return jdbc.sql("SELECT role FROM morak.participation WHERE user_id = :u").param("u", userId)
				.query(String.class).single();
	}

	private String status(long userId) {
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

	private List<String> notificationTypes(long userId) {
		return jdbc.sql("SELECT type FROM morak.notification WHERE recipient_user_id = :u ORDER BY id")
				.param("u", userId).query(String.class).list();
	}

	private String notificationBody(long userId) {
		return jdbc.sql("SELECT body FROM morak.notification WHERE recipient_user_id = :u ORDER BY id LIMIT 1")
				.param("u", userId).query(String.class).single();
	}
}

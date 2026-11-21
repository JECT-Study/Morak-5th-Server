package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.service.DiscardExpiredChallengesService.Summary;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.application.service.ApplyParticipationService;
import com.morak.feature.participation.application.service.DecideApplicationService;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.point.application.service.PointService;
import com.morak.feature.point.domain.entity.PointGrantReason;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 자동 폐기 스케줄러(위키 §4.9 ③, D-30)를 실제 PostgreSQL(Testcontainers)과 실제 트랜잭션으로 검증한다. Docker가 실행 중이어야 한다.
 * 실제 cron 실행은 끄고, 00:00 배치와 같은 순서로 {@link StartDueChallengesService#runFor} → {@link DiscardExpiredChallengesService#runFor}를
 * 직접 부른다.
 */
@SpringBootTest(properties = {
		"morak.auth.jwt.secret=integration-test-secret-integration-test-secret",
		"morak.auth.refresh-token-cleanup.cron=-",
		"morak.challenge.daily.cron=-"
})
class AutoDiscardChallengeIntegrationTest {

	private static final long HOST = 1L;
	private static final int POINT = 1_000;
	private static final AtomicInteger INVITE_SEQ = new AtomicInteger(900);
	private static final String REFUNDS = "SELECT count(*) FROM morak.point_transaction WHERE idempotency_key LIKE 'REFUND:%'";
	private static final String AUTO_DISCARD_NOTICES =
			"SELECT count(*) FROM morak.notification WHERE type = 'CHALLENGE_AUTO_DISCARDED'";

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private StartDueChallengesService startService;
	@Autowired private DiscardExpiredChallengesService discardService;
	@Autowired private ApplyParticipationService applyService;
	@Autowired private DecideApplicationService decideService;
	@Autowired private PointService pointService;
	@MockitoSpyBean private NotificationService notificationService;
	@MockitoSpyBean private ParticipationCommand participationCommand;

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

	@AfterEach
	void resetSpies() {
		reset(notificationService, participationCommand);
	}

	/**
	 * 위키 §7 "시작 예정 11/1, 인원 미달 → 11/3 23:59까지 추가 모집, 미충족 시 11/4 00:00 자동 폐기"를 한 달 뒤 날짜로 옮겼다.
	 * 실제 지원·승인 흐름이 오늘(테스트 실행일) 이후 시작일을 요구하므로 12/1 시작으로 둔다.
	 */
	@Test
	void shortChallengeIsDiscardedAtMidnightAfterRecruitDeadlineWithFullRefundAndNoticeToEveryone() {
		LocalDate dec1 = LocalDate.of(2026, 12, 1);
		long challenge = pointChallengeWithHostApprovedMemberAndPendingApplicant(dec1);

		midnight(dec1);
		assertEquals("START_HELD", status(challenge));
		midnight(dec1.plusDays(2)); // 12/3 00:00: 마감일 당일이라 아직 폐기하지 않는다.
		assertEquals("START_HELD", status(challenge));

		Summary summary = midnight(dec1.plusDays(3)); // 12/4 00:00

		assertEquals(new Summary(1, 0, 0), summary);
		assertEquals("DISCARDED", status(challenge));
		for (long user : List.of(1L, 2L, 3L)) {
			assertEquals("DISCARDED", participationColumn(challenge, user, "status"));
			assertEquals("DISCARDED", participationColumn(challenge, user, "end_reason"));
			assertEquals("REFUNDED", participationColumn(challenge, user, "point_status"));
			assertEquals(10_000, balance(user));
			// 알림 #7은 방장을 포함한 승인·대기 참가자 전원에게 간다.
			assertEquals(List.of("CHALLENGE_AUTO_DISCARDED"), discardNoticeTypes(user));
			assertTrue(discardNoticeBody(user).contains("걸어 둔 1,000P는 전액 반환됐어요"));
		}
		assertEquals(3, count(REFUNDS));
		assertEquals(0, count("SELECT count(*) FROM morak.participation_result"));
	}

	@Test
	void runningTwiceKeepsLedgerAndNotificationsUnchanged() {
		LocalDate dec1 = LocalDate.of(2026, 12, 1);
		long challenge = pointChallengeWithHostApprovedMemberAndPendingApplicant(dec1);
		midnight(dec1);
		midnight(dec1.plusDays(3));

		Summary again = discardService.runFor(dec1.plusDays(3));

		assertEquals(new Summary(0, 0, 0), again);
		assertEquals("DISCARDED", status(challenge));
		assertEquals(3, count(REFUNDS));
		assertEquals(3, count(AUTO_DISCARD_NOTICES));
		assertEquals(10_000, balance(2L));
	}

	@Test
	void sameBatchStartsBeforeDiscardingAndNeverDiscardsChallengesHeldToday() {
		LocalDate nov1 = LocalDate.of(2026, 11, 1);
		LocalDate nov4 = LocalDate.of(2026, 11, 4);
		// 11/1 보류 후 마감(11/3) 전에 3명이 찼다 → 11/4 00:00 ②가 시작하고 ③은 건드리지 않는다.
		long filled = challenge("START_HELD", nov1, "PUBLIC");
		members(filled, 3);
		// 11/1 보류, 마감까지 미달 → 같은 배치의 ③이 폐기한다.
		long expired = challenge("START_HELD", nov1, "PUBLIC");
		members(expired, 2);
		// 오늘(11/4)이 시작 예정일인 미달 챌린지 → ②가 오늘 보류하고, 마감은 11/6이라 ③은 폐기하지 않는다.
		long heldToday = challenge("RECRUITING", nov4, "PUBLIC");
		members(heldToday, 1);
		// 서버가 멈춰 놓친 모집 중 챌린지(시작 예정 10/31, 마감 11/2) → ②가 #4 없이 보류하고 같은 배치의 ③이 폐기한다.
		long missed = challenge("RECRUITING", LocalDate.of(2026, 10, 31), "PRIVATE");
		members(missed, 1);

		Summary summary = midnight(nov4);

		assertEquals(new Summary(2, 0, 0), summary);
		assertEquals("IN_PROGRESS", status(filled));
		assertEquals("DISCARDED", status(expired));
		assertEquals("START_HELD", status(heldToday));
		assertEquals("DISCARDED", status(missed));
		assertEquals(0, count("SELECT count(*) FROM morak.notification WHERE type = 'START_HELD' AND target_id = " + missed));
		assertEquals(List.of("CHALLENGE_AUTO_DISCARDED"), discardNoticeTypesFor(missed));
		// 포인트 없는 챌린지는 반환 문장이 붙지 않는다.
		assertFalse(jdbc.sql("SELECT body FROM morak.notification WHERE target_id = :c AND type = 'CHALLENGE_AUTO_DISCARDED'")
				.param("c", missed).query(String.class).single().contains("반환"));
	}

	@Test
	void notificationFailureRollsBackOnlyThatChallenge() {
		LocalDate nov1 = LocalDate.of(2026, 11, 1);
		long failing = challenge("START_HELD", nov1, "PUBLIC");
		members(failing, 2);
		long next = challenge("START_HELD", nov1, "PUBLIC");
		members(next, 2);
		doAnswer(invocation -> {
			NotifyCommand command = invocation.getArgument(0);
			if (command.targetId() == failing) {
				throw new IllegalStateException("알림 저장 실패");
			}
			return invocation.callRealMethod();
		}).when(notificationService).notify(any());

		Summary summary = discardService.runFor(LocalDate.of(2026, 11, 4));

		assertEquals(new Summary(1, 0, 1), summary);
		assertEquals("START_HELD", status(failing));
		assertEquals("ACTIVE", participationColumn(failing, HOST, "status"));
		assertEquals("DISCARDED", status(next));
		assertEquals(List.of(), discardNoticeTypesFor(failing));
	}

	@Test
	void applicationsChangedOnFirstAttemptIsRetriedInNewTransaction() {
		LocalDate dec1 = LocalDate.of(2026, 12, 1);
		long challenge = pointChallengeWithHostApprovedMemberAndPendingApplicant(dec1);
		midnight(dec1);
		// 첫 시도에서 새 신청이 끼어든 것처럼 거절하고, 두 번째 시도는 실제로 처리한다.
		// 트랜잭션 프록시를 거치지 않도록 스파이 본체에 스텁을 건다.
		ParticipationCommand spy = AopTestUtils.getTargetObject(participationCommand);
		doThrow(new BusinessException(ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED)).doCallRealMethod()
				.when(spy).discardAll(anyLong(), anyString(), any(), any());

		Summary summary = discardService.runFor(dec1.plusDays(3));

		assertEquals(new Summary(1, 0, 0), summary);
		assertEquals("DISCARDED", status(challenge));
		assertEquals(3, count(REFUNDS));
		assertEquals(3, count(AUTO_DISCARD_NOTICES));
		assertEquals(10_000, balance(3L));
	}

	/** 00:00 배치(`ChallengeDailyScheduler.runDaily`)와 같은 순서로 ② 시작 처리 → ③ 자동 폐기를 실행한다. */
	private Summary midnight(LocalDate today) {
		startService.runFor(today);
		return discardService.runFor(today);
	}

	/** 공개 포인트 챌린지: 방장(포인트 건 참여자) + 승인된 2번 + 승인 대기 3번. 모두 1,000P를 걸고 잔액 9,000P. */
	private long pointChallengeWithHostApprovedMemberAndPendingApplicant(LocalDate start) {
		long challenge = jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, visibility, category, invite_code, status, start_date,
											end_date, duration_weeks, capacity, current_count, point_amount, created_at, updated_at)
				VALUES (:owner, '아침 독서', 'PUBLIC', 'STUDY', :code, 'RECRUITING', :start, :end, 2, 5, 1, :point, now(), now())
				RETURNING id
				""")
				.param("owner", HOST)
				.param("code", String.format("%06d", INVITE_SEQ.incrementAndGet()))
				.param("start", start)
				.param("end", start.plusDays(13))
				.param("point", POINT)
				.query(Long.class)
				.single();
		pointService.grant(HOST, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:" + HOST);
		long hostParticipation = jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, deposit_amount,
												applied_at, joined_at)
				VALUES (:challenge, :user, 'HOST', 'ACTIVE', 'HELD', :point, now(), now())
				RETURNING id
				""")
				.param("challenge", challenge)
				.param("user", HOST)
				.param("point", POINT)
				.query(Long.class)
				.single();
		pointService.deposit(HOST, hostParticipation, "아침 독서", POINT);
		for (long user : new long[] {2L, 3L}) {
			pointService.grant(user, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:" + user);
		}
		long approved = applyService.apply(2L, challenge, "각오", null);
		applyService.apply(3L, challenge, "각오", null);
		decideService.approve(HOST, challenge, approved);
		assertEquals(9_000, balance(1L));
		assertEquals(9_000, balance(2L));
		assertEquals(9_000, balance(3L));
		return challenge;
	}

	/** 포인트 없는 챌린지. 기간은 7일. */
	private long challenge(String status, LocalDate start, String visibility) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, visibility, category, invite_code, status, start_date,
											end_date, duration_weeks, capacity, current_count, point_amount, created_at, updated_at)
				VALUES (:owner, '아침 독서', :visibility, 'STUDY', :code, :status, :start, :end, 1, 5, 1, 0, now(), now())
				RETURNING id
				""")
				.param("owner", HOST)
				.param("visibility", visibility)
				.param("code", String.format("%06d", INVITE_SEQ.incrementAndGet()))
				.param("status", status)
				.param("start", start)
				.param("end", start.plusDays(6))
				.query(Long.class)
				.single();
	}

	/** 방장을 포함해 승인된 참여자 count명. 합류 시각은 테스트 날짜보다 앞선 2026-10-01이다. */
	private void members(long challenge, int count) {
		for (int i = 1; i <= count; i++) {
			jdbc.sql("""
					INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, deposit_amount,
													applied_at, joined_at)
					VALUES (:challenge, :user, :role, 'ACTIVE', 'NONE', 0, TIMESTAMP '2026-10-01 00:00:00',
							TIMESTAMP '2026-10-01 00:00:00')
					""")
					.param("challenge", challenge)
					.param("user", (long) i)
					.param("role", i == 1 ? "HOST" : i == 2 ? "DEPUTY" : "MEMBER")
					.update();
		}
	}

	private String status(long challenge) {
		return jdbc.sql("SELECT status FROM morak.challenge WHERE id = :id").param("id", challenge)
				.query(String.class).single();
	}

	private String participationColumn(long challenge, long userId, String column) {
		return jdbc.sql("SELECT " + column + " FROM morak.participation WHERE challenge_id = :c AND user_id = :u")
				.param("c", challenge).param("u", userId).query(String.class).single();
	}

	private long balance(long userId) {
		return pointService.getLedger(userId, null, 1).balance();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private List<String> discardNoticeTypes(long userId) {
		return jdbc.sql("""
				SELECT type FROM morak.notification
				WHERE recipient_user_id = :u AND type IN ('CHALLENGE_DISCARDED', 'CHALLENGE_AUTO_DISCARDED') ORDER BY id
				""").param("u", userId).query(String.class).list();
	}

	private List<String> discardNoticeTypesFor(long challenge) {
		return jdbc.sql("""
				SELECT type FROM morak.notification
				WHERE target_id = :c AND type IN ('CHALLENGE_DISCARDED', 'CHALLENGE_AUTO_DISCARDED') ORDER BY id
				""").param("c", challenge).query(String.class).list();
	}

	private String discardNoticeBody(long userId) {
		return jdbc.sql("""
				SELECT body FROM morak.notification
				WHERE recipient_user_id = :u AND type = 'CHALLENGE_AUTO_DISCARDED' ORDER BY id LIMIT 1
				""").param("u", userId).query(String.class).single();
	}
}

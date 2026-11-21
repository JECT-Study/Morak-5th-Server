package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.morak.feature.challenge.application.service.StartDueChallengesService.Summary;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 시작 처리 스케줄러(위키 §4.9 ②, D-29)를 실제 PostgreSQL(Testcontainers)과 실제 트랜잭션으로 검증한다. Docker가 실행 중이어야 한다.
 * 실제 cron 실행은 끄고 {@link StartDueChallengesService#runFor}를 직접 부른다.
 */
@SpringBootTest(properties = {
		"morak.auth.jwt.secret=integration-test-secret-integration-test-secret",
		"morak.auth.refresh-token-cleanup.cron=-",
		"morak.challenge.daily.cron=-"
})
class StartChallengeIntegrationTest {

	private static final long HOST = 1L;
	private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
	private static final AtomicInteger INVITE_SEQ = new AtomicInteger(800);

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private StartDueChallengesService service;
	@MockitoSpyBean private NotificationService notificationService;

	@BeforeEach
	void clear() {
		jdbc.sql("DELETE FROM morak.notification").update();
		jdbc.sql("DELETE FROM morak.chat_message").update();
		jdbc.sql("DELETE FROM morak.participation_result").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
	}

	@AfterEach
	void resetSpy() {
		reset(notificationService);
	}

	@Test
	void startsFilledChallengeAndHoldsShortOneCountingOnlyJoinedMembers() {
		// 승인 3명(방장 포함) + 승인 대기 1명 → 시작
		long filled = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(filled, 3);
		participation(filled, 90L, "MEMBER", "PENDING");
		// 승인 2명 + 승인 대기 2명 → 승인 대기는 세지 않으므로 보류
		long shortOne = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(shortOne, 2);
		participation(shortOne, 91L, "MEMBER", "PENDING");
		participation(shortOne, 92L, "MEMBER", "PENDING");
		// 내일 시작 → 대상 아님
		long tomorrow = challenge("RECRUITING", NOV_1.plusDays(1), NOV_1.plusDays(7), "PUBLIC");
		members(tomorrow, 3);

		Summary summary = service.runFor(NOV_1);

		assertEquals(new Summary(1, 1, 0, 0), summary);
		assertEquals("IN_PROGRESS", status(filled));
		assertEquals("START_HELD", status(shortOne));
		assertEquals("RECRUITING", status(tomorrow));
		assertEquals(List.of("START_HELD:" + shortOne + ":2026-11-01"), startHeldKeys(HOST));
		assertEquals("아침 독서 최소 인원이 모이지 않아 시작이 보류됐어요. 11월 3일 23:59까지 선택해 주세요", startHeldBody(shortOne));
		assertEquals("PENDING", participationStatus(shortOne, 91L));
		assertEquals("PENDING", participationStatus(filled, 90L));
	}

	@Test
	void runningTwiceGivesSameResult() {
		long challenge = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(challenge, 2);

		service.runFor(NOV_1);
		Summary again = service.runFor(NOV_1);

		assertEquals(new Summary(0, 0, 0, 0), again);
		assertEquals("START_HELD", status(challenge));
		assertEquals(1, count("SELECT count(*) FROM morak.notification WHERE type = 'START_HELD'"));
	}

	/** 시작 예정 11/4, 11/5 23:59 전에 충족 → 11/6 00:00 시작(1일차 = 11/6). */
	@Test
	void heldChallengeFilledDuringExtraRecruitingStartsNextMidnightWithShiftedPeriod() {
		LocalDate nov4 = LocalDate.of(2026, 11, 4);
		long challenge = challenge("RECRUITING", nov4, LocalDate.of(2026, 11, 10), "PRIVATE");
		members(challenge, 1);
		service.runFor(nov4);
		assertEquals("START_HELD", status(challenge));

		assertEquals(new Summary(0, 0, 1, 0), service.runFor(LocalDate.of(2026, 11, 5)));
		participation(challenge, 2L, "DEPUTY", "ACTIVE");
		Summary summary = service.runFor(LocalDate.of(2026, 11, 6));

		assertEquals(new Summary(1, 0, 0, 0), summary);
		assertEquals("IN_PROGRESS", status(challenge));
		assertEquals(LocalDate.of(2026, 11, 6), date(challenge, "start_date"));
		assertEquals(LocalDate.of(2026, 11, 12), date(challenge, "end_date"));
	}

	/** 누락 보정: 11/1 배치를 놓쳐 11/2에 보류하면 보류한 날을 저장하고, 11/2에 다시 돌려도 시작하지 않고 11/3에 시작한다. */
	@Test
	void lateHeldChallengeStartsFromDayAfterActualHold() {
		long challenge = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(challenge, 2);
		LocalDate nov2 = LocalDate.of(2026, 11, 2);

		assertEquals(new Summary(0, 1, 0, 0), service.runFor(nov2));
		assertEquals(nov2, date(challenge, "start_held_on"));
		assertEquals(NOV_1, date(challenge, "start_date"));

		participation(challenge, 3L, "MEMBER", "ACTIVE");
		assertEquals(new Summary(0, 0, 0, 0), service.runFor(nov2));
		assertEquals("START_HELD", status(challenge));

		assertEquals(new Summary(1, 0, 0, 0), service.runFor(LocalDate.of(2026, 11, 3)));
		assertEquals("IN_PROGRESS", status(challenge));
		assertEquals(LocalDate.of(2026, 11, 3), date(challenge, "start_date"));
	}

	/**
	 * 11/1 예정(마감 11/3 23:59:59 KST) 보류 챌린지의 11/4 배치가 누락된 경우. 11/6 배치는 마감 전에 찬 챌린지만 시작하고,
	 * 마감 뒤(11/5)에 찬 챌린지는 시작하지 않는다.
	 */
	@Test
	void lateBatchStartsOnlyChallengesFilledBeforeDeadline() {
		long filledInTime = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(filledInTime, 2);
		long filledLate = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(filledLate, 2);
		service.runFor(NOV_1);
		// 11/3 23:59 KST 승인 vs 11/5 10:00 KST 승인
		participation(filledInTime, 3L, "MEMBER", "ACTIVE", Instant.parse("2026-11-03T14:59:00Z"));
		participation(filledLate, 3L, "MEMBER", "ACTIVE", Instant.parse("2026-11-05T01:00:00Z"));

		Summary summary = service.runFor(LocalDate.of(2026, 11, 6));

		assertEquals(new Summary(1, 0, 1, 0), summary);
		assertEquals("IN_PROGRESS", status(filledInTime));
		assertEquals(LocalDate.of(2026, 11, 6), date(filledInTime, "start_date"));
		assertEquals("START_HELD", status(filledLate));
	}

	/**
	 * 누락 보정: 11/1 예정 챌린지의 배치가 11/4까지 누락된 경우, 11/5 배치는 마감(11/3 23:59:59 KST) 전에 찬 챌린지만 시작하고
	 * 마감 뒤(11/4)에 찬 챌린지는 보류만 한다(기한이 지나 알림 #4 없음).
	 */
	@Test
	void missedRecruitingBatchStartsOnlyChallengesFilledBeforeDeadline() {
		long filledInTime = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(filledInTime, 2);
		participation(filledInTime, 3L, "MEMBER", "ACTIVE", Instant.parse("2026-11-02T03:00:00Z"));
		long filledLate = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(filledLate, 2);
		participation(filledLate, 3L, "MEMBER", "ACTIVE", Instant.parse("2026-11-04T03:00:00Z"));

		Summary summary = service.runFor(LocalDate.of(2026, 11, 5));

		assertEquals(new Summary(1, 1, 0, 0), summary);
		assertEquals("IN_PROGRESS", status(filledInTime));
		assertEquals(LocalDate.of(2026, 11, 5), date(filledInTime, "start_date"));
		assertEquals("START_HELD", status(filledLate));
		assertEquals(0, count("SELECT count(*) FROM morak.notification WHERE type = 'START_HELD'"));
	}

	@Test
	void notificationFailureRollsBackOnlyThatChallenge() {
		long failing = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(failing, 2);
		long next = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(next, 2);
		doAnswer(invocation -> {
			NotifyCommand command = invocation.getArgument(0);
			if (command.targetId() == failing) {
				throw new IllegalStateException("알림 저장 실패");
			}
			return invocation.callRealMethod();
		}).when(notificationService).notify(any());

		Summary summary = service.runFor(NOV_1);

		assertEquals(new Summary(0, 1, 0, 1), summary);
		assertEquals("RECRUITING", status(failing));
		assertEquals("START_HELD", status(next));
		assertEquals(1, count("SELECT count(*) FROM morak.notification WHERE type = 'START_HELD'"));
	}

	@Test
	void concurrentRunsNotifyOnlyOnce() throws Exception {
		long challenge = challenge("RECRUITING", NOV_1, NOV_1.plusDays(6), "PUBLIC");
		members(challenge, 2);
		int threads = 4;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch ready = new CountDownLatch(threads);
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Summary>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return service.runFor(NOV_1);
				}));
			}
			ready.await();
			go.countDown();
			int held = 0;
			for (Future<Summary> future : futures) {
				Summary summary = future.get(30, TimeUnit.SECONDS);
				assertEquals(0, summary.failed());
				held += summary.held();
			}
			assertEquals(1, held);
		} finally {
			pool.shutdownNow();
		}
		assertEquals("START_HELD", status(challenge));
		assertEquals(1, count("SELECT count(*) FROM morak.notification WHERE type = 'START_HELD'"));
	}

	// ---- 준비·확인용 ----

	private long challenge(String status, LocalDate start, LocalDate end, String visibility) {
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
				.param("end", end)
				.query(Long.class)
				.single();
	}

	/** 방장을 포함해 승인된 참여자 count명. */
	private void members(long challenge, int count) {
		participation(challenge, HOST, "HOST", "ACTIVE");
		for (int i = 2; i <= count; i++) {
			participation(challenge, i, i == 2 ? "DEPUTY" : "MEMBER", "ACTIVE");
		}
	}

	/** 합류 시각은 테스트 날짜(2026-11)보다 앞선 값으로 둔다. 마감 판단에 쓰이므로 필요한 테스트는 시각을 직접 준다. */
	private void participation(long challenge, long userId, String role, String status) {
		participation(challenge, userId, role, status, Instant.parse("2026-10-01T00:00:00Z"));
	}

	private void participation(long challenge, long userId, String role, String status, Instant joinedAt) {
		jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, deposit_amount,
												applied_at, joined_at)
				VALUES (:challenge, :user, :role, :status, 'NONE', 0, :joinedAt, :joinedAt)
				""")
				.param("challenge", challenge)
				.param("user", userId)
				.param("role", role)
				.param("status", status)
				.param("joinedAt", LocalDateTime.ofInstant(joinedAt, ZoneOffset.UTC))
				.update();
	}

	private String status(long challenge) {
		return jdbc.sql("SELECT status FROM morak.challenge WHERE id = :id").param("id", challenge)
				.query(String.class).single();
	}

	private LocalDate date(long challenge, String column) {
		return jdbc.sql("SELECT " + column + " FROM morak.challenge WHERE id = :id").param("id", challenge)
				.query(LocalDate.class).single();
	}

	private String participationStatus(long challenge, long userId) {
		return jdbc.sql("SELECT status FROM morak.participation WHERE challenge_id = :c AND user_id = :u")
				.param("c", challenge).param("u", userId).query(String.class).single();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private List<String> startHeldKeys(long userId) {
		return jdbc.sql("SELECT dedup_key FROM morak.notification WHERE recipient_user_id = :u AND type = 'START_HELD' ORDER BY id")
				.param("u", userId).query(String.class).list();
	}

	private String startHeldBody(long challenge) {
		return jdbc.sql("SELECT body FROM morak.notification WHERE target_id = :c AND type = 'START_HELD'")
				.param("c", challenge).query(String.class).single();
	}
}

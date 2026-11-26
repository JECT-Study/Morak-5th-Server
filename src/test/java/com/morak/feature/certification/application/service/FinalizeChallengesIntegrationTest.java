package com.morak.feature.certification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.morak.feature.certification.application.port.out.CertificationFinalizeStore;
import com.morak.feature.certification.application.port.out.CertificationFinalizeTargets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 포인트 없는 챌린지 결과 확정(위키 §4.5, §4.9 ⑧)을 실제 PostgreSQL과 실제 챌린지·참가 도메인 트랜잭션으로 검증한다.
 * Docker가 실행 중이어야 한다. 7일 챌린지(11/20~11/26)이고 정상 종료 확정 시각은 종료일 다음 날 11/27 12:05 KST다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class FinalizeChallengesIntegrationTest {

	private static final Instant DAY_AFTER_END = Instant.parse("2026-11-27T03:05:00Z");
	private static final AtomicInteger INVITE_SEQ = new AtomicInteger(800_000);

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private CertificationFinalizeStore store;
	@Autowired private CertificationFinalizeTargets targets;
	@Autowired private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.participation_result").update();
		jdbc.sql("DELETE FROM morak.certification_image").update();
		jdbc.sql("DELETE FROM morak.certification").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
	}

	@Test
	void normalEndCompletesRemainingMembersAndRecordsResultsWithoutTheExpelDay() {
		long challenge = challenge("IN_PROGRESS", "2026-11-26", 0);
		long host = participant(challenge, 1, "HOST", "ACTIVE", null);
		long member = participant(challenge, 2, "DEPUTY", "ACTIVE", null);
		// 11/22 12:05 KST 퇴장: 11/21까지만 의무다.
		long expelled = participant(challenge, 3, "MEMBER", "EXPELLED", LocalDateTime.of(2026, 11, 22, 3, 5));
		for (int d = 20; d <= 26; d++) {
			cert(challenge, host, d, d % 2 == 0 ? "APPROVED" : "AUTO_APPROVED");
			cert(challenge, member, d, d == 23 ? "FAILED" : "APPROVED");
		}
		cert(challenge, expelled, 20, "APPROVED");
		cert(challenge, expelled, 21, "APPROVED");
		cert(challenge, expelled, 22, "APPROVED"); // 퇴장일 제출은 세지 않는다(D-12)

		assertEquals(1, service(DAY_AFTER_END).run());

		assertEquals("ENDED", statusOf(challenge));
		assertEquals(List.of("1:COMPLETED:COMPLETED", "2:COMPLETED:COMPLETED", "3:EXPELLED:FAIL_LIMIT"),
				members(challenge));
		assertEquals(List.of("1:7/7:7/7:true", "2:6/7:3/3:true", "3:2/2:2/2:false"), results(challenge));
		assertEquals(LocalDateTime.ofInstant(DAY_AFTER_END, ZoneOffset.UTC), finalizedAt(challenge));
	}

	@Test
	void earlyEndedChallengeIsFinalizedTheSameDayAndRemainingMembersAreNotCompleted() {
		// 11/24 12:05 조기 종료(⑥)로 종료일이 11/24가 된 상태. 11/23까지만 의무다.
		long challenge = challenge("FINALIZING", "2026-11-24", 0);
		LocalDateTime endedAt = LocalDateTime.of(2026, 11, 24, 3, 5);
		long host = participant(challenge, 1, "HOST", "EARLY_ENDED", endedAt);
		long deputy = participant(challenge, 2, "DEPUTY", "EARLY_ENDED", endedAt);
		for (int d = 20; d <= 23; d++) {
			cert(challenge, host, d, "APPROVED");
			cert(challenge, deputy, d, d == 21 ? "REJECTED" : "AUTO_APPROVED");
		}
		cert(challenge, host, 24, "PENDING"); // 종료일 인증은 의무가 아니라 판정 대기로 남아도 확정한다

		assertEquals(1, service(Instant.parse("2026-11-24T03:05:00Z")).run());

		assertEquals("ENDED", statusOf(challenge));
		assertEquals(List.of("1:EARLY_ENDED:EARLY_END", "2:EARLY_ENDED:EARLY_END"), members(challenge));
		assertEquals(List.of("1:4/4:4/4:false", "2:3/4:2/2:false"), results(challenge));
	}

	@Test
	void pendingJudgmentLeavesTheChallengeFinalizingWithoutResults() {
		long challenge = challenge("IN_PROGRESS", "2026-11-26", 0);
		long host = participant(challenge, 1, "HOST", "ACTIVE", null);
		cert(challenge, host, 26, "PENDING");

		assertEquals(0, service(DAY_AFTER_END).run());

		assertEquals("FINALIZING", statusOf(challenge));
		assertEquals(List.of("1:ACTIVE:"), members(challenge));
		assertEquals(List.of(), results(challenge));
	}

	@Test
	void runningAgainChangesNothing() {
		long challenge = challenge("IN_PROGRESS", "2026-11-26", 0);
		participant(challenge, 1, "HOST", "ACTIVE", null);

		assertEquals(1, service(DAY_AFTER_END).run());
		assertEquals(0, service(DAY_AFTER_END.plusSeconds(86_400)).run());

		assertEquals("ENDED", statusOf(challenge));
		assertEquals(List.of("1:0/7:0/0:true"), results(challenge));
	}

	@Test
	void challengeStillRunningAndPointChallengeAreNotTouched() {
		long running = challenge("IN_PROGRESS", "2026-11-27", 0);
		participant(running, 1, "HOST", "ACTIVE", null);
		long point = challenge("IN_PROGRESS", "2026-11-26", 1_000);
		participant(point, 2, "HOST", "ACTIVE", null);

		assertEquals(0, service(DAY_AFTER_END).run());

		assertEquals("IN_PROGRESS", statusOf(running));
		assertEquals("IN_PROGRESS", statusOf(point));
		assertEquals(List.of("2:ACTIVE:"), members(point));
	}

	private FinalizeChallengesService service(Instant now) {
		return new FinalizeChallengesService(store, targets, transactionManager, Clock.fixed(now, ZoneOffset.UTC));
	}

	private long challenge(String status, String endDate, int pointAmount) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, visibility, invite_code, status, start_date,
											end_date, duration_weeks, capacity, point_amount, created_at, updated_at)
				VALUES (1, '테스트 챌린지', 'STUDY', 'PUBLIC', :code, :status, DATE '2026-11-20',
						CAST(:endDate AS date), 1, 10, :point, now(), now())
				RETURNING id
				""")
				.param("code", String.valueOf(INVITE_SEQ.incrementAndGet()))
				.param("status", status)
				.param("endDate", endDate)
				.param("point", pointAmount)
				.query(Long.class)
				.single();
	}

	private long participant(long challengeId, long userId, String role, String status, LocalDateTime endedAtUtc) {
		LocalDateTime joined = LocalDateTime.of(2026, 11, 19, 0, 0).plusMinutes(userId);
		String endReason = switch (status) {
			case "EXPELLED" -> "FAIL_LIMIT";
			case "EARLY_ENDED" -> "EARLY_END";
			default -> null;
		};
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, applied_at, joined_at,
												ended_at, end_reason)
				VALUES (:challengeId, :userId, :role, :status, 'NONE', :joined, :joined, :endedAt, :endReason)
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.param("role", role)
				.param("status", status)
				.param("joined", joined)
				.param("endedAt", endedAtUtc, java.sql.Types.TIMESTAMP)
				.param("endReason", endReason, java.sql.Types.VARCHAR)
				.query(Long.class)
				.single();
	}

	private void cert(long challengeId, long participationId, int dayOfMonth, String status) {
		jdbc.sql("""
				INSERT INTO morak.certification (challenge_id, participation_id, cert_date, status, submitted_at, updated_at)
				VALUES (:challengeId, :participationId, :day, :status, :submittedAt, now())
				""")
				.param("challengeId", challengeId)
				.param("participationId", participationId)
				.param("day", LocalDate.of(2026, 11, dayOfMonth))
				.param("status", status)
				.param("submittedAt", "FAILED".equals(status) ? null : LocalDateTime.of(2026, 11, dayOfMonth, 5, 0),
						java.sql.Types.TIMESTAMP)
				.update();
	}

	private List<String> members(long challengeId) {
		return jdbc.sql("""
				SELECT user_id || ':' || status || ':' || COALESCE(end_reason, '')
				FROM morak.participation WHERE challenge_id = :id ORDER BY user_id
				""").param("id", challengeId).query(String.class).list();
	}

	/** 사용자:승인/대상:현재/최대 연속:완주 */
	private List<String> results(long challengeId) {
		return jdbc.sql("""
				SELECT user_id || ':' || approved_count || '/' || total_count || ':' || current_streak || '/' || max_streak
					|| ':' || completed
				FROM morak.participation_result WHERE challenge_id = :id ORDER BY user_id
				""").param("id", challengeId).query(String.class).list();
	}

	private LocalDateTime finalizedAt(long challengeId) {
		return jdbc.sql("SELECT DISTINCT finalized_at FROM morak.participation_result WHERE challenge_id = :id")
				.param("id", challengeId).query(LocalDateTime.class).single();
	}

	private String statusOf(long challengeId) {
		return jdbc.sql("SELECT status FROM morak.challenge WHERE id = :id").param("id", challengeId)
				.query(String.class).single();
	}
}

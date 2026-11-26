package com.morak.feature.certification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.morak.feature.certification.application.port.out.CertificationExpelNotices;
import com.morak.feature.certification.application.port.out.CertificationExpelStore;
import com.morak.feature.certification.application.port.out.CertificationExpelTargets;
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
 * 12:05 퇴장 판정(위키 §4.4, §4.9 ⑥)을 실제 PostgreSQL과 실제 참가 도메인 트랜잭션으로 검증한다. Docker가 실행 중이어야 한다.
 * 7일 챌린지(11/20~11/26)라 퇴장 기준은 2회이고, 판정 시각은 11/24 12:05 KST다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class ExpelFailedParticipantsIntegrationTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
	private static final Instant NOW = Instant.parse("2026-11-24T03:05:00Z");
	private static final AtomicInteger INVITE_SEQ = new AtomicInteger(700_000);

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private CertificationExpelStore store;
	@Autowired private CertificationExpelTargets targets;
	@Autowired private CertificationExpelNotices notices;
	@Autowired private PlatformTransactionManager transactionManager;

	private ExpelFailedParticipantsService service;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.notification").update();
		jdbc.sql("DELETE FROM morak.certification_image").update();
		jdbc.sql("DELETE FROM morak.certification").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
		service = new ExpelFailedParticipantsService(store, targets, notices, transactionManager,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void expelsHostAndDeputyOnTheSameDayAndHandsRolesToTheNextJoiners() {
		long challenge = challenge("PUBLIC", 0);
		long host = participant(challenge, 1, "HOST");
		long deputy = participant(challenge, 2, "DEPUTY");
		participant(challenge, 3, "MEMBER");
		participant(challenge, 4, "MEMBER");
		long third = participant(challenge, 5, "MEMBER");
		fail(challenge, host, "2026-11-21", "FAILED");
		fail(challenge, host, "2026-11-22", "REJECTED");
		fail(challenge, deputy, "2026-11-22", "FAILED");
		fail(challenge, deputy, "2026-11-23", "FAILED");
		fail(challenge, third, "2026-11-23", "FAILED");
		fail(challenge, third, "2026-11-24", "REJECTED"); // 오늘 인증은 세지 않는다(D-12)

		assertEquals(2, service.run());

		assertEquals(List.of("1:MEMBER:EXPELLED:FAIL_LIMIT", "2:MEMBER:EXPELLED:FAIL_LIMIT", "3:HOST:ACTIVE:",
				"4:DEPUTY:ACTIVE:", "5:MEMBER:ACTIVE:"), members(challenge));
		assertEquals(3L, ownerOf(challenge));
		assertEquals("IN_PROGRESS", statusOf(challenge));
		assertEquals(List.of("1:EXPELLED:EXPELLED:" + host, "2:EXPELLED:EXPELLED:" + deputy), notifications());
	}

	@Test
	void publicChallengeLeftWithTwoEndsEarlyTodayAndNotifiesBoth() {
		long challenge = challenge("PUBLIC", 0);
		participant(challenge, 1, "HOST");
		participant(challenge, 2, "DEPUTY");
		long member = participant(challenge, 3, "MEMBER");
		fail(challenge, member, "2026-11-20", "FAILED");
		fail(challenge, member, "2026-11-21", "FAILED");

		assertEquals(1, service.run());

		assertEquals(List.of("1:HOST:EARLY_ENDED:EARLY_END", "2:DEPUTY:EARLY_ENDED:EARLY_END",
				"3:MEMBER:EXPELLED:FAIL_LIMIT"), members(challenge));
		assertEquals("FINALIZING", statusOf(challenge));
		assertEquals(TODAY, jdbc.sql("SELECT end_date FROM morak.challenge WHERE id = :id").param("id", challenge)
				.query(LocalDate.class).single());
		assertEquals(List.of("1:EARLY_ENDED:EARLY_ENDED:" + challenge + ":1",
				"2:EARLY_ENDED:EARLY_ENDED:" + challenge + ":2", "3:EXPELLED:EXPELLED:" + member), notifications());
	}

	@Test
	void hostExpelledIntoEarlyEndHandsOverOwnerBeforeTheChallengeCloses() {
		long challenge = challenge("PUBLIC", 0);
		long host = participant(challenge, 1, "HOST");
		participant(challenge, 2, "DEPUTY");
		participant(challenge, 3, "MEMBER");
		fail(challenge, host, "2026-11-20", "FAILED");
		fail(challenge, host, "2026-11-23", "REJECTED");

		assertEquals(1, service.run());

		assertEquals(List.of("1:MEMBER:EXPELLED:FAIL_LIMIT", "2:HOST:EARLY_ENDED:EARLY_END",
				"3:DEPUTY:EARLY_ENDED:EARLY_END"), members(challenge));
		assertEquals(2L, ownerOf(challenge));
		assertEquals("FINALIZING", statusOf(challenge));
	}

	/** TODO(D-46): 한 번에 1명 이하로 줄어도 조기 종료로 본다. */
	@Test
	void publicChallengeDroppingBelowTwoAtOnceAlsoEndsEarly() {
		long challenge = challenge("PUBLIC", 0);
		participant(challenge, 1, "HOST");
		for (long userId = 2; userId <= 4; userId++) {
			long member = participant(challenge, userId, userId == 2 ? "DEPUTY" : "MEMBER");
			fail(challenge, member, "2026-11-20", "FAILED");
			fail(challenge, member, "2026-11-21", "FAILED");
		}

		assertEquals(3, service.run());

		assertEquals(List.of("1:HOST:EARLY_ENDED:EARLY_END", "2:MEMBER:EXPELLED:FAIL_LIMIT",
				"3:MEMBER:EXPELLED:FAIL_LIMIT", "4:MEMBER:EXPELLED:FAIL_LIMIT"), members(challenge));
		assertEquals("FINALIZING", statusOf(challenge));
	}

	@Test
	void privateChallengeKeepsGoingWithTwoAndPointChallengeIsLeftToAdminReview() {
		long privateChallenge = challenge("PRIVATE", 0);
		participant(privateChallenge, 1, "HOST");
		participant(privateChallenge, 2, "DEPUTY");
		long member = participant(privateChallenge, 3, "MEMBER");
		fail(privateChallenge, member, "2026-11-20", "FAILED");
		fail(privateChallenge, member, "2026-11-21", "REJECTED");
		long pointChallenge = challenge("PUBLIC", 1_000);
		long pointMember = participant(pointChallenge, 4, "HOST");
		fail(pointChallenge, pointMember, "2026-11-20", "FAILED");
		fail(pointChallenge, pointMember, "2026-11-21", "FAILED");

		assertEquals(1, service.run());

		assertEquals(List.of("1:HOST:ACTIVE:", "2:DEPUTY:ACTIVE:", "3:MEMBER:EXPELLED:FAIL_LIMIT"),
				members(privateChallenge));
		assertEquals("IN_PROGRESS", statusOf(privateChallenge));
		assertEquals(List.of("4:HOST:ACTIVE:"), members(pointChallenge));
	}

	/** TODO(D-46): 종료일 다음 날 정오에는 판정하지 않아 마지막 날 실패로 기준에 닿아도 퇴장시키지 않는다. */
	@Test
	void dayAfterEndDateIsNotJudged() {
		long challenge = challenge("PUBLIC", 0);
		participant(challenge, 1, "HOST");
		participant(challenge, 2, "DEPUTY");
		long member = participant(challenge, 3, "MEMBER");
		fail(challenge, member, "2026-11-25", "FAILED");
		fail(challenge, member, "2026-11-26", "FAILED");
		ExpelFailedParticipantsService dayAfterEnd = new ExpelFailedParticipantsService(store, targets, notices,
				transactionManager, Clock.fixed(Instant.parse("2026-11-27T03:05:00Z"), ZoneOffset.UTC));

		assertEquals(0, dayAfterEnd.run());

		assertEquals(List.of("1:HOST:ACTIVE:", "2:DEPUTY:ACTIVE:", "3:MEMBER:ACTIVE:"), members(challenge));
	}

	@Test
	void runningAgainChangesNothing() {
		long challenge = challenge("PUBLIC", 0);
		participant(challenge, 1, "HOST");
		participant(challenge, 2, "DEPUTY");
		long member = participant(challenge, 3, "MEMBER");
		participant(challenge, 4, "MEMBER");
		fail(challenge, member, "2026-11-20", "FAILED");
		fail(challenge, member, "2026-11-21", "FAILED");

		assertEquals(1, service.run());
		assertEquals(0, service.run());

		assertEquals(1, notifications().size());
		assertEquals("IN_PROGRESS", statusOf(challenge));
	}

	private long challenge(String visibility, int pointAmount) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, visibility, invite_code, status, start_date,
											end_date, duration_weeks, capacity, point_amount, created_at, updated_at)
				VALUES (1, '테스트 챌린지', 'STUDY', :visibility, :code, 'IN_PROGRESS', DATE '2026-11-20',
						DATE '2026-11-26', 1, 10, :point, now(), now())
				RETURNING id
				""")
				.param("visibility", visibility)
				.param("code", String.valueOf(INVITE_SEQ.incrementAndGet()))
				.param("point", pointAmount)
				.query(Long.class)
				.single();
	}

	/** 사용자 번호 순서로 합류한다. */
	private long participant(long challengeId, long userId, String role) {
		LocalDateTime joined = LocalDateTime.of(2026, 11, 19, 0, 0).plusMinutes(userId);
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, applied_at, joined_at)
				VALUES (:challengeId, :userId, :role, 'ACTIVE', 'NONE', :joined, :joined)
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.param("role", role)
				.param("joined", joined)
				.query(Long.class)
				.single();
	}

	private void fail(long challengeId, long participationId, String day, String status) {
		jdbc.sql("""
				INSERT INTO morak.certification (challenge_id, participation_id, cert_date, status, submitted_at, updated_at)
				VALUES (:challengeId, :participationId, CAST(:day AS date), :status, :submittedAt, now())
				""")
				.param("challengeId", challengeId)
				.param("participationId", participationId)
				.param("day", day)
				.param("status", status)
				.param("submittedAt", "FAILED".equals(status) ? null : LocalDateTime.of(2026, 11, 20, 5, 0),
						java.sql.Types.TIMESTAMP)
				.update();
	}

	private List<String> members(long challengeId) {
		return jdbc.sql("""
				SELECT user_id || ':' || role || ':' || status || ':' || COALESCE(end_reason, '')
				FROM morak.participation WHERE challenge_id = :id ORDER BY user_id
				""").param("id", challengeId).query(String.class).list();
	}

	private List<String> notifications() {
		return jdbc.sql("""
				SELECT recipient_user_id || ':' || type || ':' || dedup_key FROM morak.notification
				ORDER BY recipient_user_id, type
				""").query(String.class).list();
	}

	private String statusOf(long challengeId) {
		return jdbc.sql("SELECT status FROM morak.challenge WHERE id = :id").param("id", challengeId)
				.query(String.class).single();
	}

	private long ownerOf(long challengeId) {
		return jdbc.sql("SELECT owner_user_id FROM morak.challenge WHERE id = :id").param("id", challengeId)
				.query(Long.class).single();
	}
}

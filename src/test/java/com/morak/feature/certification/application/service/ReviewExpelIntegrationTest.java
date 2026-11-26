package com.morak.feature.certification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.certification.application.port.out.CertificationExpelNotices;
import com.morak.feature.certification.application.port.out.CertificationExpelStore;
import com.morak.feature.certification.application.port.out.CertificationExpelTargets;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.application.port.out.CertificationJudgeNotices;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets;
import com.morak.feature.certification.application.port.out.CertificationReviewStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 운영진 검수 완료 시 퇴장 판정(위키 §4.9 ⑦, D-4, D-48)을 실제 PostgreSQL과 참가 도메인 트랜잭션으로 검증한다. Docker가 실행 중이어야
 * 한다. 7일 포인트 공개 챌린지(11/20~11/26, 1,000P)라 퇴장 기준은 2회이고, 검수 시각은 11/24 10:00 KST다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class ReviewExpelIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-24T01:00:00Z");
	private static final long ADMIN = 99;
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
	@Autowired private CertificationExpelStore expelStore;
	@Autowired private CertificationExpelTargets expelTargets;
	@Autowired private CertificationExpelNotices expelNotices;
	@Autowired private CertificationReviewStore reviewStore;
	@Autowired private CertificationJudgeStore judgeStore;
	@Autowired private CertificationJudgeTargets judgeTargets;
	@Autowired private CertificationJudgeNotices judgeNotices;
	@Autowired private CertificationImageUrls imageUrls;
	@Autowired private PlatformTransactionManager transactionManager;

	private CertificationReviewService review;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.notification").update();
		jdbc.sql("DELETE FROM morak.certification_image").update();
		jdbc.sql("DELETE FROM morak.certification").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		ExpelFailedParticipantsService expel = new ExpelFailedParticipantsService(expelStore, expelTargets,
				expelNotices, transactionManager, clock);
		review = new CertificationReviewService(reviewStore, judgeStore, judgeTargets, judgeNotices, imageUrls, expel,
				new TransactionTemplate(transactionManager), clock);
	}

	@Test
	void partialReviewDoesNotJudgeAndTheLastReviewExpelsWithPointSentence() {
		long challenge = challenge();
		participant(challenge, 1, "HOST");
		participant(challenge, 2, "DEPUTY");
		long member = participant(challenge, 3, "MEMBER");
		long other = participant(challenge, 4, "MEMBER");
		cert(challenge, member, "2026-11-21", "FAILED");
		long memberCert = cert(challenge, member, "2026-11-23", "PENDING");
		long otherCert = cert(challenge, other, "2026-11-23", "PENDING");

		review.reject(ADMIN, memberCert, "사진이 흐려요");
		assertEquals("ACTIVE", statusOf(member)); // 같은 날짜에 미판정이 남았다(D-4)

		review.approve(ADMIN, otherCert);

		assertEquals("EXPELLED", statusOf(member));
		assertEquals("IN_PROGRESS", challengeStatus(challenge));
		List<String> notices = expelNotifications();
		assertEquals(List.of("3:EXPELLED:EXPELLED:" + member), notices);
		assertTrue(expelBody(member).contains("1,000P는 전액 차감"));
	}

	@Test
	void earlierDateStillPendingHoldsJudgmentUntilItIsReviewed() {
		long challenge = challenge();
		participant(challenge, 1, "HOST");
		participant(challenge, 2, "DEPUTY");
		long member = participant(challenge, 3, "MEMBER");
		long other = participant(challenge, 4, "MEMBER");
		long earlier = cert(challenge, other, "2026-11-22", "PENDING");
		cert(challenge, member, "2026-11-22", "FAILED");
		long memberCert = cert(challenge, member, "2026-11-23", "PENDING");

		review.reject(ADMIN, memberCert, "사진이 흐려요");
		assertEquals("ACTIVE", statusOf(member)); // 11/22가 끝나지 않아 11/23까지 판정하지 않는다

		review.approve(ADMIN, earlier);

		assertEquals("EXPELLED", statusOf(member));
	}

	@Test
	void expelledMembersUnreviewedCertificationDoesNotHoldLaterJudgment() {
		long challenge = challenge();
		participant(challenge, 1, "HOST");
		participant(challenge, 2, "DEPUTY");
		participant(challenge, 3, "MEMBER");
		long expelled = participant(challenge, 4, "MEMBER");
		long member = participant(challenge, 5, "MEMBER");
		jdbc.sql("UPDATE morak.participation SET status = 'EXPELLED' WHERE id = :id").param("id", expelled).update();
		cert(challenge, expelled, "2026-11-22", "PENDING"); // 퇴장일 인증, 집계 제외(D-12)
		cert(challenge, member, "2026-11-21", "FAILED");
		long memberCert = cert(challenge, member, "2026-11-23", "PENDING");

		review.reject(ADMIN, memberCert, "사진이 흐려요");

		assertEquals("EXPELLED", statusOf(member));
	}

	@Test
	void reviewingTodaysCertificationDoesNotJudge() {
		long challenge = challenge();
		participant(challenge, 1, "HOST");
		participant(challenge, 2, "DEPUTY");
		long member = participant(challenge, 3, "MEMBER");
		participant(challenge, 4, "MEMBER");
		cert(challenge, member, "2026-11-21", "FAILED");
		long today = cert(challenge, member, "2026-11-24", "PENDING");

		review.reject(ADMIN, today, "사진이 흐려요");

		assertEquals("ACTIVE", statusOf(member)); // 오늘 인증은 세지 않는다(D-12)
	}

	@Test
	void concurrentLastTwoReviewsJudgeOnceAndEndThePublicChallengeEarly() throws Exception {
		long challenge = challenge();
		participant(challenge, 1, "HOST");
		participant(challenge, 2, "DEPUTY");
		long member = participant(challenge, 3, "MEMBER");
		cert(challenge, member, "2026-11-21", "FAILED");
		cert(challenge, member, "2026-11-23", "REJECTED");
		long hostCert = cert(challenge, participationOf(challenge, 1), "2026-11-23", "PENDING");
		long deputyCert = cert(challenge, participationOf(challenge, 2), "2026-11-23", "PENDING");

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> first = pool.submit(() -> {
				start.await();
				return review.approve(ADMIN, hostCert);
			});
			Future<?> second = pool.submit(() -> {
				start.await();
				return review.approve(ADMIN + 1, deputyCert);
			});
			start.countDown();
			first.get();
			second.get();
		} finally {
			pool.shutdown();
		}

		assertEquals("EXPELLED", statusOf(member));
		assertEquals("FINALIZING", challengeStatus(challenge));
		assertEquals(List.of("1:EARLY_ENDED:EARLY_ENDED:" + challenge + ":1",
				"2:EARLY_ENDED:EARLY_ENDED:" + challenge + ":2", "3:EXPELLED:EXPELLED:" + member),
				expelNotifications());
	}

	private long challenge() {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, visibility, invite_code, status, start_date,
											end_date, duration_weeks, capacity, point_amount, created_at, updated_at)
				VALUES (1, '포인트 챌린지', 'STUDY', 'PUBLIC', :code, 'IN_PROGRESS', DATE '2026-11-20',
						DATE '2026-11-26', 1, 10, 1000, now(), now())
				RETURNING id
				""")
				.param("code", String.valueOf(INVITE_SEQ.incrementAndGet()))
				.query(Long.class)
				.single();
	}

	/** 사용자 번호 순서로 합류한다. */
	private long participant(long challengeId, long userId, String role) {
		LocalDateTime joined = LocalDateTime.of(2026, 11, 19, 0, 0).plusMinutes(userId);
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, applied_at, joined_at)
				VALUES (:challengeId, :userId, :role, 'ACTIVE', 'HELD', :joined, :joined)
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.param("role", role)
				.param("joined", joined)
				.query(Long.class)
				.single();
	}

	private long cert(long challengeId, long participationId, String day, String status) {
		return jdbc.sql("""
				INSERT INTO morak.certification (challenge_id, participation_id, cert_date, status, submitted_at, updated_at)
				VALUES (:challengeId, :participationId, CAST(:day AS date), :status, :submittedAt, now())
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("participationId", participationId)
				.param("day", day)
				.param("status", status)
				.param("submittedAt", "FAILED".equals(status) ? null : LocalDateTime.of(2026, 11, 20, 5, 0),
						java.sql.Types.TIMESTAMP)
				.query(Long.class)
				.single();
	}

	private long participationOf(long challengeId, long userId) {
		return jdbc.sql("SELECT id FROM morak.participation WHERE challenge_id = :c AND user_id = :u")
				.param("c", challengeId).param("u", userId).query(Long.class).single();
	}

	private String statusOf(long participationId) {
		return jdbc.sql("SELECT status FROM morak.participation WHERE id = :id").param("id", participationId)
				.query(String.class).single();
	}

	private String challengeStatus(long challengeId) {
		return jdbc.sql("SELECT status FROM morak.challenge WHERE id = :id").param("id", challengeId)
				.query(String.class).single();
	}

	private List<String> expelNotifications() {
		return jdbc.sql("""
				SELECT recipient_user_id || ':' || type || ':' || dedup_key FROM morak.notification
				WHERE type IN ('EXPELLED', 'EARLY_ENDED') ORDER BY recipient_user_id, type
				""").query(String.class).list();
	}

	private String expelBody(long participationId) {
		return jdbc.sql("SELECT body FROM morak.notification WHERE dedup_key = :key")
				.param("key", "EXPELLED:" + participationId).query(String.class).single();
	}
}

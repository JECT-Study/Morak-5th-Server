package com.morak.feature.certification.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.certification.application.port.out.CertificationJudgeStore;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore.ApprovedDay;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore.PendingRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

/** 방장·부방장 판정 저장소를 실제 PostgreSQL로 검증한다. Docker가 실행 중이어야 한다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class CertificationJudgeStoreIntegrationTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 24);
	/** 11/25 12:00 KST */
	private static final Instant DUE = Instant.parse("2026-11-25T03:00:00Z");
	private static final Instant BEFORE_DUE = DUE.minusSeconds(1);

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private CertificationJudgeStore store;

	private long challengeId;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.certification_image").update();
		jdbc.sql("DELETE FROM morak.certification").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
		challengeId = jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, status, start_date, end_date,
											duration_weeks, capacity, point_amount, created_at, updated_at)
				VALUES (1, '테스트 챌린지', 'STUDY', '123456', 'IN_PROGRESS', DATE '2026-11-20', DATE '2026-11-26', 1, 5,
						0, now(), now())
				RETURNING id
				""")
				.query(Long.class)
				.single();
	}

	@Test
	void findsOnlyPendingBeforeDeadline() {
		long pending = insert(100L, "PENDING", DUE, "k1");
		insert(101L, "PENDING", null, "admin"); // 포인트 챌린지 인증(운영진 검수)
		insert(102L, "APPROVED", DUE, "k3");
		insert(103L, "PENDING", BEFORE_DUE, "k4"); // 마감 지남

		assertEquals(List.of(new PendingRow(pending, 100L, DAY, Instant.parse("2026-11-24T05:00:00Z"), DUE, "k1")),
				store.findPending(challengeId, BEFORE_DUE));
	}

	@Test
	void judgesOnlyOncePendingAndBeforeDeadline() {
		long id = insert(100L, "PENDING", DUE, "k1");

		assertFalse(store.judge(id, "APPROVED", 1L, null, DUE));
		assertTrue(store.judge(id, "REJECTED", 1L, "흐려요", BEFORE_DUE));
		assertFalse(store.judge(id, "APPROVED", 2L, null, BEFORE_DUE));

		assertEquals("REJECTED", store.findById(id).orElseThrow().status());
		assertEquals("흐려요", jdbc.sql("SELECT reject_reason FROM morak.certification WHERE id = :id")
				.param("id", id).query(String.class).single());
	}

	@Test
	void adminReviewedCertificationCannotBeJudged() {
		long id = insert(100L, "PENDING", null, "k1");

		assertFalse(store.judge(id, "APPROVED", 1L, null, BEFORE_DUE));
	}

	@Test
	void countsRejectedAndFailedAsFailures() {
		insertOn(DAY.minusDays(2), 100L, "REJECTED", DUE, null);
		insertOn(DAY.minusDays(1), 100L, "FAILED", DUE, null);
		insertOn(DAY, 100L, "APPROVED", DUE, null);
		insertOn(DAY, 101L, "FAILED", DUE, null);

		assertEquals(2, store.countFailures(100L));
	}

	@Test
	void autoApprovesOnlyOverduePendingOnceAndKeepsRejectedAndFailed() {
		long overdue = insert(100L, "PENDING", DUE, null);
		insert(101L, "PENDING", DUE.plusSeconds(86_400), null); // 오늘 제출분, 내일 정오 마감
		insert(102L, "PENDING", null, null); // 포인트 챌린지 인증(운영진 검수)
		insert(103L, "REJECTED", DUE, null);
		insert(104L, "FAILED", DUE, null);

		assertEquals(List.of(challengeId), store.findChallengesWithOverdue(DUE));
		assertEquals(List.of(new ApprovedDay(100L, DAY)), store.autoApprove(challengeId, DUE));
		assertEquals(List.of(), store.autoApprove(challengeId, DUE));
		assertEquals(List.of(), store.findChallengesWithOverdue(DUE));

		assertEquals("AUTO_APPROVED", store.findById(overdue).orElseThrow().status());
		assertEquals(List.of(new ApprovedDay(100L, DAY)), store.findAutoApproved(challengeId));
		assertEquals(List.of("PENDING", "PENDING", "REJECTED", "FAILED"), jdbc.sql("""
				SELECT status FROM morak.certification WHERE id <> :id ORDER BY participation_id
				""").param("id", overdue).query(String.class).list());
	}

	/** 11/24 12:05 KST에 퇴장한 제출자: 그날 낸 인증은 판정할 수 없었으므로 경고에서 빠지고, 전날 경고는 남는다. */
	@Test
	void expelledSubmittersCertificationsFromTheExpulsionDayAreNotCountedAsWarnings() {
		long expelled = jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, point_status, applied_at, joined_at,
												ended_at, end_reason)
				VALUES (:challengeId, 7, 'MEMBER', 'EXPELLED', 'NONE', now(), now(), TIMESTAMP '2026-11-24 03:05:00',
						'FAIL_LIMIT')
				RETURNING id
				""").param("challengeId", challengeId).query(Long.class).single();
		insertOn(DAY.minusDays(1), expelled, "AUTO_APPROVED", BEFORE_DUE.minusSeconds(86_400), null);
		insertOn(DAY, expelled, "PENDING", DUE, null);
		insertOn(DAY, 100L, "PENDING", DUE, null);

		assertEquals(List.of(new ApprovedDay(100L, DAY)), store.autoApprove(challengeId, DUE));
		assertEquals(List.of(new ApprovedDay(expelled, DAY.minusDays(1)), new ApprovedDay(100L, DAY)),
				store.findAutoApproved(challengeId).stream()
						.sorted(java.util.Comparator.comparing(ApprovedDay::certDate)).toList());
		assertEquals(2L, jdbc.sql("SELECT count(*) FROM morak.certification WHERE cert_date = :day AND status = 'AUTO_APPROVED'")
				.param("day", DAY).query(Long.class).single());
	}

	private long insert(long participationId, String status, Instant judgeDueAt, String imageKey) {
		return insertOn(DAY, participationId, status, judgeDueAt, imageKey);
	}

	private long insertOn(LocalDate day, long participationId, String status, Instant judgeDueAt, String imageKey) {
		boolean failed = "FAILED".equals(status);
		long id = jdbc.sql("""
				INSERT INTO morak.certification (challenge_id, participation_id, cert_date, status, submitted_at,
												judge_due_at, updated_at)
				VALUES (:challengeId, :participationId, :day, :status, :submittedAt, :due, now())
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("participationId", participationId)
				.param("day", day)
				.param("status", status)
				.param("submittedAt", failed ? null : LocalDateTime.of(2026, 11, 24, 5, 0), java.sql.Types.TIMESTAMP)
				.param("due", judgeDueAt == null ? null : LocalDateTime.ofInstant(judgeDueAt, ZoneOffset.UTC),
						java.sql.Types.TIMESTAMP)
				.query(Long.class)
				.single();
		if (imageKey != null) {
			jdbc.sql("INSERT INTO morak.certification_image (certification_id, image_key, sort_order) VALUES (:id, :key, 0)")
					.param("id", id)
					.param("key", imageKey)
					.update();
		}
		return id;
	}
}

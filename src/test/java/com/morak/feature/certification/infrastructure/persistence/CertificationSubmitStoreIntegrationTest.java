package com.morak.feature.certification.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.certification.application.port.out.CertificationSubmitStore;
import com.morak.feature.challenge.application.query.ChallengeQuery;
import com.morak.feature.challenge.application.query.ChallengeQuery.CertificationWindow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 인증 제출 저장과 미제출 FAILED 행 스키마를 실제 PostgreSQL로 검증한다. Docker가 실행 중이어야 한다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class CertificationSubmitStoreIntegrationTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 24);
	private static final Instant SUBMITTED = Instant.parse("2026-11-24T05:00:00Z");
	private static final Instant DUE = Instant.parse("2026-11-25T03:00:00Z");

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
	private CertificationSubmitStore store;

	@Autowired
	private ChallengeQuery challengeQuery;

	private long challengeId;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.certification").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
		challengeId = jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, status, start_date, end_date,
											duration_weeks, capacity, point_amount, created_at, updated_at)
				VALUES (1, '테스트 챌린지', 'STUDY', '123456', 'IN_PROGRESS', DATE '2026-11-20', DATE '2026-11-26', 1, 5,
						1000, now(), now())
				RETURNING id
				""")
				.query(Long.class)
				.single();
	}

	@Test
	void readsTheCertificationWindowOfAChallenge() {
		assertEquals(Optional.of(new CertificationWindow("IN_PROGRESS", LocalDate.of(2026, 11, 20),
				LocalDate.of(2026, 11, 26), 1000)), challengeQuery.findCertificationWindow(challengeId));
		assertTrue(challengeQuery.findCertificationWindow(challengeId + 1).isEmpty());
	}

	@Test
	void savesAPendingCertificationWithItsImageOncePerDay() {
		assertFalse(store.exists(100L, DAY));

		long id = store.save(challengeId, 100L, DAY, SUBMITTED, DUE, "certifications/1/a").orElseThrow();

		assertTrue(store.exists(100L, DAY));
		var row = jdbc.sql("SELECT status, submitted_at, judge_due_at FROM morak.certification WHERE id = :id")
				.param("id", id)
				.query((rs, rowNum) -> List.of(rs.getString("status"),
						rs.getObject("submitted_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
						rs.getObject("judge_due_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)))
				.single();
		assertEquals(List.of("PENDING", SUBMITTED, DUE), row);
		assertEquals(List.of("certifications/1/a"), imageKeys(id));

		// 같은 참가자·날짜의 두 번째 저장은 아무것도 남기지 않는다.
		assertTrue(store.save(challengeId, 100L, DAY, SUBMITTED, DUE, "certifications/1/b").isEmpty());
		assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM morak.certification_image").query(Integer.class).single());
		// 다음 날이나 다른 참가자는 따로 저장된다.
		assertTrue(store.save(challengeId, 100L, DAY.plusDays(1), SUBMITTED, DUE, "certifications/1/c").isPresent());
		assertTrue(store.save(challengeId, 101L, DAY, SUBMITTED, DUE, "certifications/1/d").isPresent());
	}

	@Test
	void adminReviewedCertificationsHaveNoJudgeDue() {
		long id = store.save(challengeId, 100L, DAY, SUBMITTED, null, "certifications/1/a").orElseThrow();

		assertNull(jdbc.sql("SELECT judge_due_at FROM morak.certification WHERE id = :id")
				.param("id", id)
				.query(LocalDateTime.class)
				.optional()
				.orElse(null));
	}

	@Test
	void onlyFailedRowsMayHaveNoSubmissionTime() {
		assertEquals(1, insertWithoutSubmission("FAILED"));
		assertThrows(DataIntegrityViolationException.class, () -> insertWithoutSubmission("PENDING"));
	}

	private int insertWithoutSubmission(String status) {
		return jdbc.sql("""
				INSERT INTO morak.certification (challenge_id, participation_id, cert_date, status, updated_at)
				VALUES (:challengeId, 200, :day, :status, now())
				""")
				.param("challengeId", challengeId)
				.param("day", DAY)
				.param("status", status)
				.update();
	}

	private List<String> imageKeys(long certificationId) {
		return jdbc.sql("SELECT image_key FROM morak.certification_image WHERE certification_id = :id")
				.param("id", certificationId)
				.query(String.class)
				.list();
	}
}

package com.morak.feature.certification.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.morak.feature.certification.application.port.out.CertificationFailureStore;
import java.time.Instant;
import java.time.LocalDate;
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

/** 00:00 미제출 FAILED 생성(위키 §4.4, §4.9 ①)을 실제 PostgreSQL로 검증한다. Docker가 실행 중이어야 한다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class CertificationFailureStoreIntegrationTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 20);
	private static final LocalDate END = LocalDate.of(2026, 11, 26);
	private static final Instant NOW = Instant.parse("2026-11-23T15:00:00Z");

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

	/** 초대 코드·사용자 ID 유니크 제약을 피하는 일련번호. */
	private long seq = 100_000;

	@Autowired
	private CertificationFailureStore store;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.certification_image").update();
		jdbc.sql("DELETE FROM morak.certification").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
	}

	@Test
	void createsFailedOnlyForActiveParticipantsWithoutSubmissionUpToLastDate() {
		long challenge = challenge("IN_PROGRESS", START, END);
		long submitter = participant(challenge, "ACTIVE");
		long missing = participant(challenge, "ACTIVE");
		participant(challenge, "EXPELLED");
		submitted(challenge, submitter, START);

		int created = store.insertMissing(START.plusDays(1), NOW);

		// 제출자는 제출한 날(11/20)을 빼고 11/21만, 미제출자는 11/20·11/21, 퇴장자는 없음
		assertEquals(3, created);
		assertEquals(List.of(START.plusDays(1)), failedDates(submitter));
		assertEquals(List.of(START, START.plusDays(1)), failedDates(missing));
		assertEquals("PENDING", jdbc.sql("SELECT status FROM morak.certification WHERE participation_id = :p")
				.param("p", submitter).query(String.class).list().getFirst());
	}

	@Test
	void rerunCreatesNothingMore() {
		long challenge = challenge("IN_PROGRESS", START, END);
		long missing = participant(challenge, "ACTIVE");

		store.insertMissing(START, NOW);
		int second = store.insertMissing(START, NOW);

		assertEquals(0, second);
		assertEquals(List.of(START), failedDates(missing));
	}

	@Test
	void doesNotGoBeyondEndDateOrTouchChallengesNotInProgress() {
		long ended = challenge("FINALIZING", START, START.plusDays(1));
		long endedMember = participant(ended, "ACTIVE");
		long recruiting = challenge("RECRUITING", START, END);
		long waiting = participant(recruiting, "ACTIVE");
		long early = challenge("FINALIZING", START, START.plusDays(1));
		long earlyEnded = participant(early, "EARLY_ENDED");

		store.insertMissing(START.plusDays(3), NOW);

		assertEquals(List.of(START, START.plusDays(1)), failedDates(endedMember));
		assertEquals(List.of(), failedDates(waiting));
		assertEquals(List.of(), failedDates(earlyEnded));
	}

	@Test
	void createsNothingBeforeStartDate() {
		long challenge = challenge("IN_PROGRESS", START, END);
		long member = participant(challenge, "ACTIVE");

		assertEquals(0, store.insertMissing(START.minusDays(1), NOW));
		assertEquals(List.of(), failedDates(member));
	}

	private long challenge(String status, LocalDate start, LocalDate end) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, status, start_date, end_date,
											duration_weeks, capacity, point_amount, created_at, updated_at)
				VALUES (1, '테스트 챌린지', 'STUDY', :code, :status, :start, :end, 1, 5, 0, now(), now())
				RETURNING id
				""")
				.param("code", String.valueOf(++seq))
				.param("status", status)
				.param("start", start)
				.param("end", end)
				.query(Long.class)
				.single();
	}

	private long participant(long challengeId, String status) {
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, status, applied_at, joined_at)
				VALUES (:challengeId, :userId, :status, now(), now())
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("userId", ++seq)
				.param("status", status)
				.query(Long.class)
				.single();
	}

	private void submitted(long challengeId, long participationId, LocalDate day) {
		jdbc.sql("""
				INSERT INTO morak.certification (challenge_id, participation_id, cert_date, status, submitted_at, updated_at)
				VALUES (:challengeId, :participationId, :day, 'PENDING', now(), now())
				""")
				.param("challengeId", challengeId)
				.param("participationId", participationId)
				.param("day", day)
				.update();
	}

	private List<LocalDate> failedDates(long participationId) {
		return jdbc.sql("""
				SELECT cert_date FROM morak.certification
				WHERE participation_id = :p AND status = 'FAILED' AND submitted_at IS NULL AND judge_due_at IS NULL
				ORDER BY cert_date
				""")
				.param("p", participationId)
				.query(LocalDate.class)
				.list();
	}
}

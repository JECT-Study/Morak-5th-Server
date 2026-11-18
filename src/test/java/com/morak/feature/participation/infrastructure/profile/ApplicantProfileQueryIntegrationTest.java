package com.morak.feature.participation.infrastructure.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.certification.application.query.CertificationQuery;
import com.morak.feature.certification.application.query.CertificationQuery.CertificationStats;
import com.morak.feature.medal.application.query.MedalQuery;
import com.morak.feature.medal.application.query.MedalQuery.MedalInfo;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.Application;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipationRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

/** 신청 목록의 신청자 프로필을 만드는 조회(누적 인증 횟수, 메달, 참가 기록, 처리한 신청)를 실제 PostgreSQL로 검증한다. Docker가 실행 중이어야 한다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class ApplicantProfileQueryIntegrationTest {

	private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 1, 10, 0);

	private final AtomicInteger inviteSeq = new AtomicInteger();

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
	private ParticipationQuery participationQuery;

	@Autowired
	private CertificationQuery certificationQuery;

	@Autowired
	private MedalQuery medalQuery;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.certification").update();
		jdbc.sql("DELETE FROM morak.user_medal").update();
		jdbc.sql("DELETE FROM morak.medal").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
	}

	@Test
	void certificationStatsSumAcrossChallengesAndSkipUndecided() {
		long first = challenge("IN_PROGRESS");
		long second = challenge("IN_PROGRESS");
		long inFirst = participation(first, 7L, "MEMBER", "ACTIVE");
		long inSecond = participation(second, 7L, "MEMBER", "ACTIVE");
		long other = participation(first, 8L, "MEMBER", "ACTIVE");
		// 7번 사용자: 의무 15회 + 10회 중 20회 승인 (챌린지별 평균이 아니라 합산)
		certifications(first, inFirst, "APPROVED", 10, 1);
		certifications(first, inFirst, "REJECTED", 3, 11);
		certifications(first, inFirst, "FAILED", 2, 14);
		certifications(second, inSecond, "AUTO_APPROVED", 10, 1);
		// 판정 대기는 분자·분모에서 모두 뺀다.
		certifications(second, inSecond, "PENDING", 4, 11);
		certifications(first, other, "REJECTED", 2, 1);

		Map<Long, CertificationStats> stats = certificationQuery.findStatsByUserIds(List.of(7L, 8L, 9L));

		assertEquals(new CertificationStats(20, 25), stats.get(7L));
		assertEquals(new CertificationStats(0, 2), stats.get(8L));
		assertFalse(stats.containsKey(9L));
		assertTrue(certificationQuery.findStatsByUserIds(List.of()).isEmpty());
	}

	@Test
	void earnedMedalsAreListedOncePerMedalInEarnedOrder() {
		long medal1 = medal("COMPLETE_1", "완주 메달 1-1");
		long medal2 = medal("COMPLETE_3", "완주 메달 1-2");
		userMedal(7L, medal2, 1L, T0.plusDays(2));
		userMedal(7L, medal1, 1L, T0.plusDays(1));
		userMedal(7L, medal1, 2L, T0.plusDays(3));

		Map<Long, List<MedalInfo>> medals = medalQuery.findEarnedByUserIds(List.of(7L, 8L));

		assertEquals(
				List.of(new MedalInfo("COMPLETE_1", "완주 메달 1-1", null), new MedalInfo("COMPLETE_3", "완주 메달 1-2", null)),
				medals.get(7L));
		assertFalse(medals.containsKey(8L));
		assertTrue(medalQuery.findEarnedByUserIds(List.of()).isEmpty());
	}

	@Test
	void participationRecordsCountOnlyCompletedAndExpelled() {
		long challenge = challenge("ENDED");
		participation(challenge, 7L, "MEMBER", "COMPLETED");
		participation(challenge("ENDED"), 7L, "MEMBER", "COMPLETED");
		participation(challenge("ENDED"), 7L, "MEMBER", "EXPELLED");
		participation(challenge("ENDED"), 7L, "MEMBER", "EARLY_ENDED");
		participation(challenge("RECRUITING"), 7L, "MEMBER", "PENDING");
		participation(challenge, 8L, "MEMBER", "ACTIVE");

		Map<Long, ParticipationRecord> records = participationQuery.findRecordsByUserIds(List.of(7L, 8L));

		assertEquals(new ParticipationRecord(2, 1), records.get(7L));
		assertFalse(records.containsKey(8L));
		assertTrue(participationQuery.findRecordsByUserIds(List.of()).isEmpty());
	}

	@Test
	void decidedApplicationsExcludeTheHostAndFilterByStatus() {
		long challenge = challenge("RECRUITING");
		participation(challenge, 1L, "HOST", "ACTIVE");
		long approved = pending(challenge, 2L, "ACTIVE", "승인된 각오", T0.plusHours(1));
		long rejected = pending(challenge, 3L, "REJECTED", "거절된 각오", T0.plusHours(2));
		pending(challenge, 4L, "PENDING", "대기 각오", T0.plusHours(3));

		List<Application> active = participationQuery.findDecidedApplications(challenge, "ACTIVE");
		List<Application> rejectedList = participationQuery.findDecidedApplications(challenge, "REJECTED");

		assertEquals(List.of(approved), active.stream().map(Application::participationId).toList());
		assertEquals("승인된 각오", active.get(0).applyMessage());
		assertFalse(active.get(0).invalidated());
		assertEquals(List.of(rejected), rejectedList.stream().map(Application::participationId).toList());
	}

	private long challenge(String status) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, status, start_date, end_date,
											duration_weeks, capacity, created_at, updated_at)
				VALUES (1, '테스트 챌린지', 'STUDY', :code, :status, DATE '2026-11-10', DATE '2026-11-23', 2, 5, now(), now())
				RETURNING id
				""")
				.param("code", String.format("%06d", inviteSeq.incrementAndGet()))
				.param("status", status)
				.query(Long.class)
				.single();
	}

	private long participation(long challengeId, long userId, String role, String status) {
		return pending(challengeId, userId, role, status, null, T0);
	}

	private long pending(long challengeId, long userId, String status, String applyMessage, LocalDateTime appliedAt) {
		return pending(challengeId, userId, "MEMBER", status, applyMessage, appliedAt);
	}

	private long pending(
			long challengeId, long userId, String role, String status, String applyMessage, LocalDateTime appliedAt) {
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, apply_message, applied_at)
				VALUES (:challengeId, :userId, :role, :status, :message, :appliedAt)
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.param("role", role)
				.param("status", status)
				.param("message", applyMessage)
				.param("appliedAt", appliedAt)
				.query(Long.class)
				.single();
	}

	/** startDay일부터 count일 연속으로 같은 상태의 인증을 만든다. */
	private void certifications(long challengeId, long participationId, String status, int count, int startDay) {
		for (int i = 0; i < count; i++) {
			jdbc.sql("""
					INSERT INTO morak.certification (challenge_id, participation_id, cert_date, status, submitted_at, updated_at)
					VALUES (:challengeId, :participationId, :certDate, :status, now(), now())
					""")
					.param("challengeId", challengeId)
					.param("participationId", participationId)
					.param("certDate", LocalDate.of(2026, 11, 1).plusDays(startDay - 1L + i))
					.param("status", status)
					.update();
		}
	}

	private long medal(String code, String name) {
		return jdbc.sql("INSERT INTO morak.medal (code, name) VALUES (:code, :name) RETURNING id")
				.param("code", code)
				.param("name", name)
				.query(Long.class)
				.single();
	}

	private void userMedal(long userId, long medalId, long challengeId, LocalDateTime earnedAt) {
		jdbc.sql("INSERT INTO morak.user_medal (user_id, medal_id, challenge_id, earned_at) VALUES (:u, :m, :c, :at)")
				.param("u", userId)
				.param("m", medalId)
				.param("c", challengeId)
				.param("at", earnedAt)
				.update();
	}
}

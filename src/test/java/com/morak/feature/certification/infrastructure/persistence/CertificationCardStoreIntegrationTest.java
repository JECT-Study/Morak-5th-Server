package com.morak.feature.certification.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.certification.application.port.out.CertificationCardStore;
import com.morak.feature.certification.application.port.out.CertificationCardStore.CardRow;
import com.morak.feature.certification.application.port.out.CertificationCardStore.PhotoRow;
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

/** 홈 카드 인증 집계(위키 §4.5 팀 달성률, D-23 ⑤~⑦)를 실제 PostgreSQL로 검증한다. Docker가 실행 중이어야 한다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class CertificationCardStoreIntegrationTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 1);
	private static final LocalDate END = START.plusDays(13);
	private static final Instant LATER = Instant.parse("2026-12-01T00:00:00Z");

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
	private CertificationCardStore store;

	private long seq = 200_000;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.certification_image").update();
		jdbc.sql("DELETE FROM morak.certification").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
	}

	@Test
	void teamTargetExcludesDaysFromExpulsionLikeWikiExample() {
		// 위키 §7: 5명 × 14일, A가 4일차(11/4) 12:05에 퇴장. 퇴장 전 3일 모두 실패, 퇴장일 제출은 집계에서 뺀다.
		long challenge = challenge("FINALIZING", 0);
		long host = participant(challenge, "HOST", "ACTIVE", null);
		for (int i = 0; i < 3; i++) {
			participant(challenge, "MEMBER", "ACTIVE", null);
		}
		long expelled = participant(challenge, "MEMBER", "EXPELLED", Instant.parse("2026-11-04T03:05:00Z"));
		for (int day = 0; day < 3; day++) {
			cert(challenge, expelled, START.plusDays(day), "FAILED", null);
		}
		cert(challenge, expelled, START.plusDays(3), "REJECTED", "2026-11-04T01:00:00Z");

		CardRow card = store.findCards(List.of(host), END.plusDays(2), LATER).getFirst();

		assertEquals(59L, card.teamTarget());
		assertEquals(3, card.teamFailed());
		assertFalse(card.todayOpen());
	}

	@Test
	void expulsionDayIsTheKstDateEvenWhenUtcDateIsTheDayBefore() {
		// 운영진 검수 완료 시 퇴장(#91)은 시각이 정해져 있지 않다. 11/4 08:00 KST = 11/3 23:00 UTC → 퇴장일은 11/4(KST)
		long challenge = challenge("FINALIZING", 0);
		long host = participant(challenge, "HOST", "ACTIVE", null);
		for (int i = 0; i < 3; i++) {
			participant(challenge, "MEMBER", "ACTIVE", null);
		}
		participant(challenge, "MEMBER", "EXPELLED", Instant.parse("2026-11-03T23:00:00Z"));

		CardRow card = store.findCards(List.of(host), END.plusDays(2), LATER).getFirst();

		// 4명 × 14일 + 퇴장자 3일(11/1~11/3). UTC 날짜로 잘못 자르면 58이 된다
		assertEquals(59L, card.teamTarget());
	}

	@Test
	void countsOnlyUpToYesterdayAndFillsTodayFieldsWhileInProgress() {
		LocalDate today = START.plusDays(2);
		long challenge = challenge("IN_PROGRESS", 0);
		long host = participant(challenge, "HOST", "ACTIVE", null);
		long deputy = participant(challenge, "DEPUTY", "ACTIVE", null);
		long member = participant(challenge, "MEMBER", "ACTIVE", null);
		cert(challenge, member, START, "FAILED", null);
		cert(challenge, deputy, START.plusDays(1), "PENDING", "2026-11-02T01:00:00Z");
		cert(challenge, host, today, "PENDING", "2026-11-03T00:30:00Z");
		long approved = cert(challenge, member, today, "APPROVED", "2026-11-03T01:00:00Z");
		image(approved, "member-today");
		cert(challenge, member, today.plusDays(1), "FAILED", null);
		// 판정 목록(D-42)에서 빠지는 것: 마감(11/2 12:00 KST)이 지난 PENDING, 11/2에 퇴장한 팀원의 PENDING
		cert(challenge, deputy, START, "PENDING", "2026-11-01T01:00:00Z");
		long expelled = participant(challenge, "MEMBER", "EXPELLED", Instant.parse("2026-11-02T03:05:00Z"));
		cert(challenge, expelled, START.plusDays(1), "PENDING", "2026-11-02T01:00:00Z");

		// 11/3 10:00 KST
		List<CardRow> cards = store.findCards(List.of(host, deputy, member), today, Instant.parse("2026-11-03T01:00:00Z"));
		CardRow hostCard = cards.stream().filter(c -> c.participationId() == host).findFirst().orElseThrow();
		CardRow deputyCard = cards.stream().filter(c -> c.participationId() == deputy).findFirst().orElseThrow();
		CardRow memberCard = cards.stream().filter(c -> c.participationId() == member).findFirst().orElseThrow();

		// 어제(11/2)까지 3명 × 2일 + 퇴장자 1일(11/1), 실패 1(오늘 이후 행은 세지 않는다)
		assertEquals(7L, hostCard.teamTarget());
		assertEquals(1, hostCard.teamFailed());
		assertTrue(hostCard.todayOpen());
		assertEquals(3, hostCard.todayTarget());
		assertEquals(2, hostCard.todayCertified());
		assertEquals("PENDING", hostCard.myTodayStatus());
		assertNull(deputyCard.myTodayStatus());
		assertEquals("APPROVED", memberCard.myTodayStatus());
		// 방장은 마감 전 방장 아닌 팀원의 PENDING(부방장 11/2 1건), 부방장은 방장 인증만(1), 일반 팀원은 0
		assertEquals(1, hostCard.pendingJudgeCount());
		assertEquals(1, deputyCard.pendingJudgeCount());
		assertEquals(0, memberCard.pendingJudgeCount());

		assertEquals(List.of(new PhotoRow(challenge, userOf(member), "member-today",
				Instant.parse("2026-11-03T01:00:00Z"))), store.findApprovedPhotos(List.of(challenge), today));
	}

	@Test
	void notStartedChallengeHasNoTargetAndPointChallengeHasNoJudgeWork() {
		long recruiting = challenge("START_HELD", 0);
		long waitingHost = participant(recruiting, "HOST", "ACTIVE", null);
		long pointChallenge = challenge("IN_PROGRESS", 1000);
		long pointHost = participant(pointChallenge, "HOST", "ACTIVE", null);
		long pointMember = participant(pointChallenge, "MEMBER", "ACTIVE", null);
		cert(pointChallenge, pointMember, START, "PENDING", "2026-11-01T01:00:00Z");

		List<CardRow> cards = store.findCards(List.of(waitingHost, pointHost), START.plusDays(1), LATER);

		CardRow waiting = cards.stream().filter(c -> c.participationId() == waitingHost).findFirst().orElseThrow();
		assertNull(waiting.teamTarget());
		assertFalse(waiting.todayOpen());
		CardRow point = cards.stream().filter(c -> c.participationId() == pointHost).findFirst().orElseThrow();
		assertEquals(2L, point.teamTarget());
		assertEquals(0, point.pendingJudgeCount());
	}

	private long challenge(String status, int pointAmount) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, status, start_date, end_date,
											duration_weeks, capacity, point_amount, created_at, updated_at)
				VALUES (1, '테스트 챌린지', 'STUDY', :code, :status, :start, :end, 2, 5, :point, now(), now())
				RETURNING id
				""")
				.param("code", String.valueOf(++seq))
				.param("status", status)
				.param("start", START)
				.param("end", END)
				.param("point", pointAmount)
				.query(Long.class)
				.single();
	}

	private long participant(long challengeId, String role, String status, Instant endedAt) {
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at, joined_at, ended_at)
				VALUES (:challengeId, :userId, :role, :status, now(), now(), :endedAt)
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("userId", ++seq)
				.param("role", role)
				.param("status", status)
				.param("endedAt", endedAt == null ? null : LocalDateTime.ofInstant(endedAt, ZoneOffset.UTC),
						java.sql.Types.TIMESTAMP)
				.query(Long.class)
				.single();
	}

	private long userOf(long participationId) {
		return jdbc.sql("SELECT user_id FROM morak.participation WHERE id = :id")
				.param("id", participationId)
				.query(Long.class)
				.single();
	}

	private long cert(long challengeId, long participationId, LocalDate day, String status, String submittedAt) {
		return jdbc.sql("""
				INSERT INTO morak.certification
					(challenge_id, participation_id, cert_date, status, submitted_at, judge_due_at, updated_at)
				VALUES (:challengeId, :participationId, :day, :status, :submittedAt, :judgeDueAt, now())
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("participationId", participationId)
				.param("day", day)
				.param("status", status)
				.param("submittedAt", submittedAt == null ? null
						: LocalDateTime.ofInstant(Instant.parse(submittedAt), ZoneOffset.UTC), java.sql.Types.TIMESTAMP)
				// 제출한 인증은 다음 날 12:00 KST(= 다음 날 03:00 UTC) 마감(D-34 ⑤)
				.param("judgeDueAt", submittedAt == null ? null : day.plusDays(1).atTime(3, 0), java.sql.Types.TIMESTAMP)
				.query(Long.class)
				.single();
	}

	private void image(long certificationId, String key) {
		jdbc.sql("INSERT INTO morak.certification_image (certification_id, image_key, sort_order) VALUES (:id, :key, 0)")
				.param("id", certificationId)
				.param("key", key)
				.update();
	}
}

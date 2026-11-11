package com.morak.feature.participation.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.Application;
import com.morak.feature.participation.application.query.ParticipationQuery.MyParticipation;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipantResult;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipantSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** 챌린지 도메인에 제공하는 참가자 조회를 실제 PostgreSQL(Testcontainers)로 검증한다. Docker가 실행 중이어야 한다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class JdbcParticipationQueryIntegrationTest {

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
	private ParticipationQuery query;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.chat_message").update();
		jdbc.sql("DELETE FROM morak.participation_result").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
	}

	@Test
	void countJoinedCountsOnlyParticipantsStillInTheRoom() {
		long challenge = challenge("RECRUITING");
		participation(challenge, 1L, "HOST", "ACTIVE", T0);
		participation(challenge, 2L, "MEMBER", "ACTIVE", T0.plusHours(1));
		participation(challenge, 3L, "MEMBER", "EXPEL_NOTICED", T0.plusHours(2));
		participation(challenge, 4L, "MEMBER", "PENDING", null);
		participation(challenge, 5L, "MEMBER", "REJECTED", null);
		participation(challenge, 6L, "MEMBER", "EXPELLED", T0.plusHours(3));

		assertEquals(3, query.countJoined(challenge));
	}

	@Test
	void countJoinedByChallengeIdsFillsZeroForChallengesWithoutParticipants() {
		long first = challenge("RECRUITING");
		long second = challenge("RECRUITING");
		long empty = challenge("RECRUITING");
		participation(first, 1L, "HOST", "ACTIVE", T0);
		participation(first, 2L, "MEMBER", "ACTIVE", T0);
		participation(second, 3L, "HOST", "ACTIVE", T0);
		participation(second, 4L, "MEMBER", "PENDING", null);

		Map<Long, Integer> counts = query.countJoinedByChallengeIds(List.of(first, second, empty));

		assertEquals(Map.of(first, 2, second, 1, empty, 0), counts);
		assertTrue(query.countJoinedByChallengeIds(List.of()).isEmpty());
	}

	@Test
	void countByStatusGroupsEveryStatus() {
		long challenge = challenge("RECRUITING");
		participation(challenge, 1L, "HOST", "ACTIVE", T0);
		participation(challenge, 2L, "MEMBER", "PENDING", null);
		participation(challenge, 3L, "MEMBER", "PENDING", null);
		participation(challenge, 4L, "MEMBER", "REJECTED", null);

		assertEquals(Map.of("ACTIVE", 1, "PENDING", 2, "REJECTED", 1), query.countByStatus(challenge));
		assertTrue(query.countByStatus(-1L).isEmpty());
	}

	@Test
	void findMembersReturnsConfirmedParticipantsInJoinOrder() {
		long challenge = challenge("IN_PROGRESS");
		long deputy = participation(challenge, 2L, "DEPUTY", "ACTIVE", T0.plusHours(1));
		long host = participation(challenge, 1L, "HOST", "ACTIVE", T0);
		long noticed = participation(challenge, 3L, "MEMBER", "EXPEL_NOTICED", T0.plusHours(2));
		long completed = participation(challenge, 4L, "MEMBER", "COMPLETED", T0.plusHours(3));
		long earlyEnded = participation(challenge, 5L, "MEMBER", "EARLY_ENDED", T0.plusHours(4));
		participation(challenge, 6L, "MEMBER", "PENDING", null);
		participation(challenge, 7L, "MEMBER", "EXPELLED", T0.plusHours(5));
		participation(challenge, 8L, "MEMBER", "DISCARDED", null);

		List<ParticipantSummary> members = query.findMembers(challenge);

		assertEquals(List.of(host, deputy, noticed, completed, earlyEnded),
				members.stream().map(ParticipantSummary::participationId).toList());
		ParticipantSummary first = members.get(0);
		assertEquals(1L, first.userId());
		assertEquals("HOST", first.role());
		assertEquals("ACTIVE", first.status());
		assertEquals(Instant.parse("2026-11-01T10:00:00Z"), first.joinedAt());
	}

	@Test
	void findParticipantsFiltersByTheGivenStatuses() {
		long challenge = challenge("START_HELD");
		long host = participation(challenge, 1L, "HOST", "ACTIVE", T0);
		long pending = participation(challenge, 2L, "MEMBER", "PENDING", null);
		participation(challenge, 3L, "MEMBER", "REJECTED", null);

		List<ParticipantSummary> targets = query.findParticipants(challenge, Set.of("ACTIVE", "PENDING"));

		assertEquals(List.of(host, pending), targets.stream().map(ParticipantSummary::participationId).toList());
		assertNull(targets.get(1).joinedAt());
		assertTrue(query.findParticipants(challenge, Set.of()).isEmpty());
	}

	@Test
	void findPendingApplicationsListsOldestFirstWithApplyMessage() {
		long challenge = challenge("RECRUITING");
		participation(challenge, 1L, "HOST", "ACTIVE", T0);
		long later = pending(challenge, 3L, "나중에 신청", T0.plusDays(2));
		long earlier = pending(challenge, 2L, "먼저 신청", T0.plusDays(1));
		participation(challenge, 4L, "MEMBER", "REJECTED", null);
		participation(challenge, 5L, "MEMBER", "INVALIDATED", null);

		List<Application> applications = query.findPendingApplications(challenge);

		assertEquals(List.of(earlier, later), applications.stream().map(Application::participationId).toList());
		assertEquals(2L, applications.get(0).userId());
		assertEquals("먼저 신청", applications.get(0).applyMessage());
		assertEquals(Instant.parse("2026-11-02T10:00:00Z"), applications.get(0).appliedAt());
	}

	@Test
	void findMyParticipationReturnsRowRegardlessOfStatus() {
		long challenge = challenge("RECRUITING");
		long rejected = participation(challenge, 2L, "MEMBER", "REJECTED", null);

		MyParticipation mine = query.findMyParticipation(challenge, 2L).orElseThrow();

		assertEquals(rejected, mine.participationId());
		assertEquals(challenge, mine.challengeId());
		assertEquals("REJECTED", mine.status());
		assertEquals("MEMBER", mine.role());
		assertNull(mine.joinedAt());
		assertTrue(query.findMyParticipation(challenge, 99L).isEmpty());
	}

	@Test
	void findMyParticipationsFiltersByStatusNewestApplicationFirst() {
		long older = challenge("IN_PROGRESS");
		long newer = challenge("RECRUITING");
		long ended = challenge("ENDED");
		long other = challenge("RECRUITING");
		long olderParticipation = pending(older, 1L, null, T0);
		jdbc.sql("UPDATE morak.participation SET status = 'ACTIVE' WHERE id = :id")
				.param("id", olderParticipation)
				.update();
		long newerParticipation = pending(newer, 1L, "새 신청", T0.plusDays(3));
		pending(ended, 1L, null, T0.plusDays(5));
		jdbc.sql("UPDATE morak.participation SET status = 'COMPLETED' WHERE challenge_id = :id")
				.param("id", ended)
				.update();
		pending(other, 2L, null, T0);

		List<MyParticipation> mine = query.findMyParticipations(1L, Set.of("PENDING", "ACTIVE"));

		assertEquals(List.of(newerParticipation, olderParticipation),
				mine.stream().map(MyParticipation::participationId).toList());
		assertEquals("새 신청", mine.get(0).applyMessage());
		assertTrue(query.findMyParticipations(1L, Set.of()).isEmpty());
	}

	@Test
	void countOngoingCountsOnlyJoinedParticipationsInLiveChallenges() {
		long recruiting = challenge("RECRUITING");
		long held = challenge("START_HELD");
		long inProgress = challenge("IN_PROGRESS");
		long finalizing = challenge("FINALIZING");
		long ended = challenge("ENDED");
		long discarded = challenge("DISCARDED");
		long pendingOnly = challenge("RECRUITING");
		long deleted = challenge("RECRUITING");
		jdbc.sql("UPDATE morak.challenge SET deleted_at = now() WHERE id = :id").param("id", deleted).update();

		participation(recruiting, 1L, "HOST", "ACTIVE", T0);
		participation(held, 1L, "MEMBER", "ACTIVE", T0);
		participation(inProgress, 1L, "MEMBER", "EXPEL_NOTICED", T0);
		participation(finalizing, 1L, "MEMBER", "ACTIVE", T0);
		participation(ended, 1L, "MEMBER", "COMPLETED", T0);
		participation(discarded, 1L, "MEMBER", "DISCARDED", T0);
		participation(pendingOnly, 1L, "MEMBER", "PENDING", null);
		participation(deleted, 1L, "MEMBER", "ACTIVE", T0);
		participation(recruiting, 2L, "MEMBER", "ACTIVE", T0);

		assertEquals(3, query.countOngoing(1L));
		assertEquals(1, query.countOngoing(2L));
		assertEquals(0, query.countOngoing(99L));
	}

	@Test
	void findResultsListsFinalizedResultsByAchievementRate() {
		long challenge = challenge("ENDED");
		long low = participation(challenge, 1L, "HOST", "COMPLETED", T0);
		long high = participation(challenge, 2L, "MEMBER", "COMPLETED", T0);
		result(low, challenge, 1L, 10, 14, "71.43", 3, 5, false);
		result(high, challenge, 2L, 14, 14, "100.00", 14, 14, true);

		List<ParticipantResult> results = query.findResults(challenge);

		assertEquals(List.of(high, low), results.stream().map(ParticipantResult::participationId).toList());
		ParticipantResult top = results.get(0);
		assertEquals(14, top.approvedCount());
		assertEquals(14, top.totalCount());
		assertEquals(0, new BigDecimal("100.00").compareTo(top.achievementRate()));
		assertEquals(14, top.maxStreak());
		assertTrue(top.completed());
		assertEquals(Instant.parse("2026-11-20T00:00:00Z"), top.finalizedAt());
		assertTrue(query.findResults(-1L).isEmpty());
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

	private long participation(long challengeId, long userId, String role, String status, LocalDateTime joinedAt) {
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at, joined_at)
				VALUES (:challengeId, :userId, :role, :status, :appliedAt, :joinedAt)
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.param("role", role)
				.param("status", status)
				.param("appliedAt", T0)
				.param("joinedAt", joinedAt)
				.query(Long.class)
				.single();
	}

	private long pending(long challengeId, long userId, String applyMessage, LocalDateTime appliedAt) {
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, apply_message, applied_at)
				VALUES (:challengeId, :userId, 'MEMBER', 'PENDING', :message, :appliedAt)
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.param("message", applyMessage)
				.param("appliedAt", appliedAt)
				.query(Long.class)
				.single();
	}

	private void result(long participationId, long challengeId, long userId, int approved, int total, String rate,
			int currentStreak, int maxStreak, boolean completed) {
		jdbc.sql("""
				INSERT INTO morak.participation_result (participation_id, challenge_id, user_id, approved_count,
					total_count, achievement_rate, current_streak, max_streak, completed, finalized_at)
				VALUES (:participationId, :challengeId, :userId, :approved, :total, :rate, :currentStreak, :maxStreak,
					:completed, TIMESTAMP '2026-11-20 00:00:00')
				""")
				.param("participationId", participationId)
				.param("challengeId", challengeId)
				.param("userId", userId)
				.param("approved", approved)
				.param("total", total)
				.param("rate", new BigDecimal(rate))
				.param("currentStreak", currentStreak)
				.param("maxStreak", maxStreak)
				.param("completed", completed)
				.update();
	}
}

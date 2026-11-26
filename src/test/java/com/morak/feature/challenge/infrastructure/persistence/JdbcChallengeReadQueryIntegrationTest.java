package com.morak.feature.challenge.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.challenge.application.query.ChallengeReadQuery;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeDetail;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
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

/** 챌린지 일괄 조회와 초대 코드 조회를 실제 PostgreSQL(Testcontainers)로 검증한다. Docker가 실행 중이어야 한다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class JdbcChallengeReadQueryIntegrationTest {

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
	private ChallengeReadQuery query;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.chat_message").update();
		jdbc.sql("DELETE FROM morak.participation_result").update();
		jdbc.sql("DELETE FROM morak.participation").update();
		jdbc.sql("DELETE FROM morak.challenge").update();
	}

	@Test
	void findByIdsReturnsOnlyExistingNotDeletedChallenges() {
		long recruiting = challenge("RECRUITING");
		long ended = challenge("ENDED");
		long deleted = challenge("IN_PROGRESS");
		jdbc.sql("UPDATE morak.challenge SET deleted_at = now() WHERE id = :id").param("id", deleted).update();

		List<ChallengeDetail> found = query.findByIds(List.of(recruiting, ended, deleted, -1L));

		assertEquals(Set.of(recruiting, ended), found.stream().map(ChallengeDetail::id).collect(Collectors.toSet()));
		assertEquals(ChallengeStatus.ENDED,
				found.stream().filter(challenge -> challenge.id() == ended).findFirst().orElseThrow().status());
		assertTrue(query.findByIds(List.of()).isEmpty());
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

	@Test
	void findsPrivateChallengeByInviteCode() {
		long id = challenge("123456", "PRIVATE", "START_HELD", false);

		var found = query.findByInviteCode("123456").orElseThrow();

		assertEquals(id, found.id());
		assertEquals(ChallengeVisibility.PRIVATE, found.visibility());
		assertEquals(ChallengeStatus.START_HELD, found.status());
	}

	@Test
	void ignoresUnknownAndDeletedInviteCodes() {
		challenge("123456", "PUBLIC", "RECRUITING", true);

		assertTrue(query.findByInviteCode("123456").isEmpty());
		assertTrue(query.findByInviteCode("999999").isEmpty());
	}

	private long challenge(String code, String visibility, String status, boolean deleted) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, visibility, status, start_date,
											end_date, duration_weeks, capacity, created_at, updated_at, deleted_at)
				VALUES (1, '테스트 챌린지', 'STUDY', :code, :visibility, :status, DATE '2026-12-01', DATE '2026-12-07', 1, 5,
						now(), now(), CASE WHEN :deleted THEN now() END)
				RETURNING id
				""")
				.param("code", code)
				.param("visibility", visibility)
				.param("status", status)
				.param("deleted", deleted)
				.query(Long.class)
				.single();
	}
}

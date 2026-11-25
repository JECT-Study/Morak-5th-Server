package com.morak.feature.certification.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.morak.feature.certification.application.port.out.CertificationFeedStore;
import com.morak.feature.certification.application.port.out.CertificationFeedStore.FeedRow;
import com.morak.feature.certification.application.port.out.CertificationLikeStore;
import java.util.Optional;
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

/** 인증 피드 노출 조건(위키 §4.4: 다른 팀원에게는 승인된 사진만)과 좋아요를 실제 PostgreSQL로 검증한다. Docker가 실행 중이어야 한다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class CertificationFeedStoreIntegrationTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 24);
	private static final long ME = 100L;
	private static final long OTHER = 200L;
	private static final long ME_USER = 900L;
	private static final Instant NOW = Instant.parse("2026-11-24T07:00:00Z");

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
	private CertificationFeedStore store;

	@Autowired
	private CertificationLikeStore likes;

	private long challengeId;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.certification_reaction").update();
		jdbc.sql("DELETE FROM morak.certification_image").update();
		jdbc.sql("DELETE FROM morak.certification").update();
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
	void showsApprovedCertificationsOfOthersAndAllButFailedOfMine() {
		long otherApproved = insert(OTHER, "APPROVED", "2026-11-24T05:00:00Z", "o1");
		long otherAuto = insert(OTHER + 1, "AUTO_APPROVED", "2026-11-24T03:00:00Z", "o2");
		insert(OTHER + 2, "PENDING", "2026-11-24T04:00:00Z", "o3");
		insert(OTHER + 3, "REJECTED", "2026-11-24T04:30:00Z", "o4");
		insert(OTHER + 4, "FAILED", null, null);
		long minePending = insert(ME, "PENDING", "2026-11-24T06:00:00Z", "m1");

		List<FeedRow> rows = store.find(challengeId, DAY, ME, ME_USER);

		assertEquals(List.of(
				new FeedRow(otherAuto, OTHER + 1, "AUTO_APPROVED", Instant.parse("2026-11-24T03:00:00Z"), "o2", 0, false),
				new FeedRow(otherApproved, OTHER, "APPROVED", Instant.parse("2026-11-24T05:00:00Z"), "o1", 0, false),
				new FeedRow(minePending, ME, "PENDING", Instant.parse("2026-11-24T06:00:00Z"), "m1", 0, false)), rows);
	}

	@Test
	void showsMyRejectedCertificationButNotMyFailedOne() {
		long mineRejected = insert(ME, "REJECTED", "2026-11-24T06:00:00Z", "m1");
		insertOn(DAY.plusDays(1), ME, "FAILED", null, null);

		assertEquals(List.of(new FeedRow(mineRejected, ME, "REJECTED", Instant.parse("2026-11-24T06:00:00Z"), "m1", 0, false)),
				store.find(challengeId, DAY, ME, ME_USER));
		assertEquals(List.of(), store.find(challengeId, DAY.plusDays(1), ME, ME_USER));
	}

	@Test
	void readsOnlyTheRequestedDateAndChallenge() {
		insertOn(DAY.minusDays(1), OTHER, "APPROVED", "2026-11-23T05:00:00Z", "old");

		assertEquals(List.of(), store.find(challengeId, DAY, ME, ME_USER));
		assertEquals(List.of(), store.find(challengeId + 1, DAY.minusDays(1), ME, ME_USER));
	}

	@Test
	void feedShowsLikeCountAndWhetherTheViewerLiked() {
		long liked = insert(OTHER, "APPROVED", "2026-11-24T05:00:00Z", "o1");
		long notLiked = insert(OTHER + 1, "APPROVED", "2026-11-24T06:00:00Z", "o2");
		likes.like(liked, ME_USER, NOW);
		likes.like(liked, 901L, NOW);
		likes.like(notLiked, 901L, NOW);

		assertEquals(List.of(
				new FeedRow(liked, OTHER, "APPROVED", Instant.parse("2026-11-24T05:00:00Z"), "o1", 2, true),
				new FeedRow(notLiked, OTHER + 1, "APPROVED", Instant.parse("2026-11-24T06:00:00Z"), "o2", 1, false)),
				store.find(challengeId, DAY, ME, ME_USER));
	}

	@Test
	void likingTwiceKeepsOneLikeAndUnlikingTwiceIsHarmless() {
		long id = insert(OTHER, "APPROVED", "2026-11-24T05:00:00Z", "o1");

		likes.like(id, ME_USER, NOW);
		likes.like(id, ME_USER, NOW);
		assertEquals(1, likes.count(id));

		likes.unlike(id, ME_USER);
		likes.unlike(id, ME_USER);
		assertEquals(0, likes.count(id));
	}

	@Test
	void findsStatusOnlyWithinTheChallenge() {
		long id = insert(OTHER, "PENDING", "2026-11-24T05:00:00Z", "o1");

		assertEquals(Optional.of("PENDING"), likes.findStatus(challengeId, id));
		assertEquals(Optional.empty(), likes.findStatus(challengeId + 1, id));
	}

	private long insert(long participationId, String status, String submittedAt, String imageKey) {
		return insertOn(DAY, participationId, status, submittedAt, imageKey);
	}

	private long insertOn(LocalDate day, long participationId, String status, String submittedAt, String imageKey) {
		LocalDateTime submitted = submittedAt == null ? null
				: LocalDateTime.ofInstant(Instant.parse(submittedAt), ZoneOffset.UTC);
		long id = jdbc.sql("""
				INSERT INTO morak.certification (challenge_id, participation_id, cert_date, status, submitted_at, updated_at)
				VALUES (:challengeId, :participationId, :day, :status, :submittedAt, now())
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("participationId", participationId)
				.param("day", day)
				.param("status", status)
				.param("submittedAt", submitted, java.sql.Types.TIMESTAMP)
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

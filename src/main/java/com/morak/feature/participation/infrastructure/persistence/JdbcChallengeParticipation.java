package com.morak.feature.participation.infrastructure.persistence;

import com.morak.feature.participation.application.ChallengeParticipation;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcChallengeParticipation implements ChallengeParticipation {

	private final JdbcClient jdbc;

	JdbcChallengeParticipation(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public long countOngoing(long userId) {
		// 위키 §4.3 [제안]: 퇴장 예정도 실제 퇴장 전까지 포함한다. §8 결정 번호는 미부여.
		return jdbc.sql("""
				SELECT count(*) FROM morak.participation p
				JOIN morak.challenge c ON c.id = p.challenge_id
				WHERE p.user_id = :userId AND p.status IN ('ACTIVE', 'EXPEL_NOTICED')
				AND c.status IN ('RECRUITING', 'START_HELD', 'IN_PROGRESS') AND c.deleted_at IS NULL
				""")
				.param("userId", userId)
				.query(Long.class)
				.single();
	}

	@Override
	public void registerHost(long challengeId, long userId, Instant now) {
		jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at, joined_at)
				VALUES (:challengeId, :userId, 'HOST', 'ACTIVE', :now, :now)
				""")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
				.update();
	}

	/** 챌린지와 신청을 차례로 잠그고, 포인트 처리가 필요한 신청이 없을 때만 일괄 무효화한다. */
	@Override
	public Optional<List<InvalidatedApplication>> invalidatePendingWithoutPoints(long userId) {
		jdbc.sql("""
				SELECT c.id FROM morak.challenge c
				JOIN morak.participation p ON p.challenge_id = c.id
				WHERE p.user_id = :userId AND p.status = 'PENDING'
				ORDER BY c.id FOR UPDATE OF c
				""")
				.param("userId", userId)
				.query(Long.class)
				.list();
		var pending = jdbc.sql("""
				SELECT p.id, c.id AS challenge_id, c.owner_user_id,
					(p.deposit_amount <> 0 OR p.deposit_status <> 'NONE') AS requires_point_handling
				FROM morak.participation p JOIN morak.challenge c ON c.id = p.challenge_id
				WHERE p.user_id = :userId AND p.status = 'PENDING'
				ORDER BY p.id FOR UPDATE OF p
				""")
				.param("userId", userId)
				.query((rs, rowNum) -> new PendingApplication(
						new InvalidatedApplication(rs.getLong("id"), rs.getLong("challenge_id"), rs.getLong("owner_user_id")),
						rs.getBoolean("requires_point_handling")))
				.list();
		if (pending.stream().anyMatch(PendingApplication::requiresPointHandling)) {
			return Optional.empty();
		}
		var invalidated = pending.stream().map(PendingApplication::application).toList();
		if (!invalidated.isEmpty()) {
			jdbc.sql("UPDATE morak.participation SET status = 'INVALIDATED' WHERE id IN (:ids)")
					.param("ids", invalidated.stream().map(InvalidatedApplication::id).toList())
					.update();
		}
		return Optional.of(invalidated);
	}

	private record PendingApplication(InvalidatedApplication application, boolean requiresPointHandling) {}
}

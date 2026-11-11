package com.morak.feature.participation.infrastructure.persistence;

import com.morak.feature.participation.application.query.ParticipationQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 참가자 엔티티가 생기기 전까지의 조회 구현. 엔티티/리포지토리가 생기면 그쪽으로 교체한다. */
@Component
public class JdbcParticipationQuery implements ParticipationQuery {

	/** 현재 방에 남아 있는 참가자. 승인 대기·종료·퇴장 완료는 제외한다. */
	private static final String IN_ROOM = "'ACTIVE', 'EXPEL_NOTICED'";

	/** 합류가 확정된 적이 있는 참가자. 진행 중이거나 정상·조기 종료까지 마친 사람이다. */
	private static final String MEMBERS = "'ACTIVE', 'EXPEL_NOTICED', 'COMPLETED', 'EARLY_ENDED'";

	private static final String MY_PARTICIPATION_COLUMNS =
			"id, challenge_id, role, status, apply_message, applied_at, joined_at, ended_at, end_reason";

	private final JdbcClient jdbc;

	public JdbcParticipationQuery(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<ParticipantInfo> findJoinedParticipant(Long challengeId, Long userId) {
		return jdbc.sql("""
				SELECT id, user_id, role, status FROM morak.participation
				WHERE challenge_id = :challengeId AND user_id = :userId
				AND status IN ('ACTIVE', 'EXPEL_NOTICED', 'COMPLETED')
				ORDER BY id DESC LIMIT 1
				""")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.query((rs, rowNum) -> toInfo(rs))
				.optional();
	}

	@Override
	public Map<Long, ParticipantInfo> findParticipantsByIds(Collection<Long> participationIds) {
		Map<Long, ParticipantInfo> result = new HashMap<>();
		if (participationIds.isEmpty()) {
			return result;
		}
		jdbc.sql("SELECT id, user_id, role, status FROM morak.participation WHERE id IN (:ids)")
				.param("ids", participationIds)
				.query((rs, rowNum) -> {
					ParticipantInfo info = toInfo(rs);
					result.put(info.participationId(), info);
					return info;
				})
				.list();
		return result;
	}

	@Override
	public int countJoined(Long challengeId) {
		return jdbc.sql("SELECT COUNT(*) FROM morak.participation WHERE challenge_id = :challengeId AND status IN ("
						+ IN_ROOM + ")")
				.param("challengeId", challengeId)
				.query(Integer.class)
				.single();
	}

	@Override
	public Map<Long, Integer> countJoinedByChallengeIds(Collection<Long> challengeIds) {
		Map<Long, Integer> result = new LinkedHashMap<>();
		if (challengeIds.isEmpty()) {
			return result;
		}
		challengeIds.forEach(id -> result.put(id, 0));
		jdbc.sql("""
				SELECT challenge_id, COUNT(*) AS cnt FROM morak.participation
				WHERE challenge_id IN (:ids) AND status IN (%s)
				GROUP BY challenge_id
				""".formatted(IN_ROOM))
				.param("ids", challengeIds)
				.query((rs, rowNum) -> {
					result.put(rs.getLong("challenge_id"), rs.getInt("cnt"));
					return rowNum;
				})
				.list();
		return result;
	}

	@Override
	public Map<String, Integer> countByStatus(Long challengeId) {
		Map<String, Integer> result = new LinkedHashMap<>();
		jdbc.sql("""
				SELECT status, COUNT(*) AS cnt FROM morak.participation
				WHERE challenge_id = :challengeId
				GROUP BY status ORDER BY status
				""")
				.param("challengeId", challengeId)
				.query((rs, rowNum) -> {
					result.put(rs.getString("status"), rs.getInt("cnt"));
					return rowNum;
				})
				.list();
		return result;
	}

	@Override
	public List<ParticipantSummary> findMembers(Long challengeId) {
		return jdbc.sql("""
				SELECT id, user_id, role, status, joined_at FROM morak.participation
				WHERE challenge_id = :challengeId AND status IN (%s)
				ORDER BY joined_at, id
				""".formatted(MEMBERS))
				.param("challengeId", challengeId)
				.query((rs, rowNum) -> toSummary(rs))
				.list();
	}

	@Override
	public List<ParticipantSummary> findParticipants(Long challengeId, Set<String> statuses) {
		if (statuses.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT id, user_id, role, status, joined_at FROM morak.participation
				WHERE challenge_id = :challengeId AND status IN (:statuses)
				ORDER BY joined_at, applied_at, id
				""")
				.param("challengeId", challengeId)
				.param("statuses", statuses)
				.query((rs, rowNum) -> toSummary(rs))
				.list();
	}

	@Override
	public List<Application> findPendingApplications(Long challengeId) {
		return jdbc.sql("""
				SELECT id, user_id, apply_message, applied_at FROM morak.participation
				WHERE challenge_id = :challengeId AND status = 'PENDING'
				ORDER BY applied_at, id
				""")
				.param("challengeId", challengeId)
				.query((rs, rowNum) -> new Application(
						rs.getLong("id"),
						rs.getLong("user_id"),
						rs.getString("apply_message"),
						toInstant(rs, "applied_at")))
				.list();
	}

	@Override
	public Optional<MyParticipation> findMyParticipation(Long challengeId, Long userId) {
		return jdbc.sql("SELECT " + MY_PARTICIPATION_COLUMNS
						+ " FROM morak.participation WHERE challenge_id = :challengeId AND user_id = :userId")
				.param("challengeId", challengeId)
				.param("userId", userId)
				.query((rs, rowNum) -> toMyParticipation(rs))
				.optional();
	}

	@Override
	public List<MyParticipation> findMyParticipations(Long userId, Set<String> statuses) {
		if (statuses.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT %s FROM morak.participation
				WHERE user_id = :userId AND status IN (:statuses)
				ORDER BY applied_at DESC, id DESC
				""".formatted(MY_PARTICIPATION_COLUMNS))
				.param("userId", userId)
				.param("statuses", statuses)
				.query((rs, rowNum) -> toMyParticipation(rs))
				.list();
	}

	@Override
	public int countOngoing(Long userId) {
		// 챌린지 상태는 challenge 도메인 소유라 읽기 전용 조인만 한다. 한도 판단이 한 쿼리로 일관되게 끝나야 해서 조인을 쓴다.
		return jdbc.sql("""
				SELECT COUNT(*) FROM morak.participation p
				JOIN morak.challenge c ON c.id = p.challenge_id
				WHERE p.user_id = :userId
				AND p.status IN (%s)
				AND c.status IN ('RECRUITING', 'START_HELD', 'IN_PROGRESS')
				AND c.deleted_at IS NULL
				""".formatted(IN_ROOM))
				.param("userId", userId)
				.query(Integer.class)
				.single();
	}

	@Override
	public List<ParticipantResult> findResults(Long challengeId) {
		return jdbc.sql("""
				SELECT participation_id, user_id, approved_count, total_count, achievement_rate,
					current_streak, max_streak, completed, finalized_at
				FROM morak.participation_result
				WHERE challenge_id = :challengeId
				ORDER BY achievement_rate DESC, participation_id
				""")
				.param("challengeId", challengeId)
				.query((rs, rowNum) -> new ParticipantResult(
						rs.getLong("participation_id"),
						rs.getLong("user_id"),
						rs.getInt("approved_count"),
						rs.getInt("total_count"),
						rs.getBigDecimal("achievement_rate"),
						rs.getInt("current_streak"),
						rs.getInt("max_streak"),
						rs.getBoolean("completed"),
						toInstant(rs, "finalized_at")))
				.list();
	}

	private static ParticipantInfo toInfo(ResultSet rs) throws SQLException {
		return new ParticipantInfo(
				rs.getLong("id"), rs.getLong("user_id"), rs.getString("role"), rs.getString("status"));
	}

	private static ParticipantSummary toSummary(ResultSet rs) throws SQLException {
		return new ParticipantSummary(
				rs.getLong("id"),
				rs.getLong("user_id"),
				rs.getString("role"),
				rs.getString("status"),
				toInstant(rs, "joined_at"));
	}

	private static MyParticipation toMyParticipation(ResultSet rs) throws SQLException {
		return new MyParticipation(
				rs.getLong("id"),
				rs.getLong("challenge_id"),
				rs.getString("role"),
				rs.getString("status"),
				rs.getString("apply_message"),
				toInstant(rs, "applied_at"),
				toInstant(rs, "joined_at"),
				toInstant(rs, "ended_at"),
				rs.getString("end_reason"));
	}

	/** DB의 TIMESTAMP는 UTC로 저장한다(위키 §3). NULL이면 null. */
	private static Instant toInstant(ResultSet rs, String column) throws SQLException {
		LocalDateTime value = rs.getObject(column, LocalDateTime.class);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}
}

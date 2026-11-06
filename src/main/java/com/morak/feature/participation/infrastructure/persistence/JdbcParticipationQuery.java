package com.morak.feature.participation.infrastructure.persistence;

import com.morak.feature.participation.application.query.ParticipationQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 참가자 엔티티가 생기기 전까지의 조회 구현. 엔티티/리포지토리가 생기면 그쪽으로 교체한다. */
@Component
public class JdbcParticipationQuery implements ParticipationQuery {

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

	private static ParticipantInfo toInfo(ResultSet rs) throws SQLException {
		return new ParticipantInfo(
				rs.getLong("id"), rs.getLong("user_id"), rs.getString("role"), rs.getString("status"));
	}
}

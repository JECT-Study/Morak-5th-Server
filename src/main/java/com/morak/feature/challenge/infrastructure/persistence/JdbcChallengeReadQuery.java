package com.morak.feature.challenge.infrastructure.persistence;

import com.morak.feature.challenge.application.query.ChallengeReadQuery;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeDetail;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeSummary;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcChallengeReadQuery implements ChallengeReadQuery {

	private final JdbcClient jdbc;

	public JdbcChallengeReadQuery(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ChallengeSummary> findRecruitingPublic(ChallengeCategory category, String keyword, Long cursor, int size) {
		StringBuilder sql = new StringBuilder("""
				SELECT id, title, description, category, start_date, end_date, capacity, current_count,
					point_amount, cover_image_key
				FROM morak.challenge
				WHERE deleted_at IS NULL AND visibility = 'PUBLIC' AND status IN ('RECRUITING', 'START_HELD')
				AND current_count < capacity
				""");
		if (category != null) sql.append(" AND category = :category");
		// TODO(D-13): MVP에서는 DB에서 제목을 검색한다.
		if (keyword != null && !keyword.isBlank()) sql.append(" AND title ILIKE :keyword");
		if (cursor != null) sql.append(" AND id < :cursor");
		sql.append(" ORDER BY id DESC LIMIT :size");
		var query = jdbc.sql(sql.toString()).param("size", size);
		if (category != null) query.param("category", category.name());
		if (keyword != null && !keyword.isBlank()) query.param("keyword", "%" + escapeLike(keyword.trim()) + "%");
		if (cursor != null) query.param("cursor", cursor);
		return query.query((rs, rowNum) -> new ChallengeSummary(
				rs.getLong("id"), rs.getString("title"), rs.getString("description"),
				ChallengeCategory.valueOf(rs.getString("category")),
				rs.getObject("start_date", java.time.LocalDate.class), rs.getObject("end_date", java.time.LocalDate.class),
				rs.getInt("capacity"), rs.getInt("current_count"), rs.getInt("point_amount"),
				rs.getString("cover_image_key"))).list();
	}

	@Override
	public Optional<ChallengeDetail> findById(long challengeId) {
		return jdbc.sql("""
				SELECT id, owner_user_id, title, description, visibility, category, purpose, common_mission, verification_rules,
					expectations, status, start_date, end_date, capacity, current_count, point_amount, cover_image_key
				FROM morak.challenge WHERE id = :id AND deleted_at IS NULL
				""")
				.param("id", challengeId)
				.query((rs, rowNum) -> new ChallengeDetail(
					rs.getLong("id"), rs.getLong("owner_user_id"), rs.getString("title"), rs.getString("description"),
					rs.getString("visibility"),
					ChallengeCategory.valueOf(rs.getString("category")), rs.getString("purpose"), rs.getString("common_mission"),
					rs.getString("verification_rules"), rs.getString("expectations"), rs.getString("status"),
					rs.getObject("start_date", java.time.LocalDate.class), rs.getObject("end_date", java.time.LocalDate.class),
					rs.getInt("capacity"), rs.getInt("current_count"), rs.getInt("point_amount"),
					rs.getString("cover_image_key")))
				.optional();
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}

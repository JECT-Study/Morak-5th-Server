package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.port.out.CertificationFeedStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 인증 피드 조회. DB의 DATETIME은 UTC다. */
@Component
class JdbcCertificationFeedStore implements CertificationFeedStore {

	private final JdbcClient jdbc;

	JdbcCertificationFeedStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<FeedRow> find(long challengeId, LocalDate certDate, long viewerParticipationId, long viewerUserId) {
		return jdbc.sql("""
				SELECT c.id, c.participation_id, c.status, c.submitted_at, ci.image_key,
					(SELECT COUNT(*) FROM morak.certification_reaction r
						WHERE r.certification_id = c.id AND r.reaction_type = 'LIKE') AS like_count,
					EXISTS (SELECT 1 FROM morak.certification_reaction r
						WHERE r.certification_id = c.id AND r.reaction_type = 'LIKE' AND r.user_id = :viewerUser) AS liked
				FROM morak.certification c
				LEFT JOIN morak.certification_image ci ON ci.certification_id = c.id AND ci.sort_order = 0
				WHERE c.challenge_id = :challengeId AND c.cert_date = :certDate
				AND (c.status IN ('APPROVED', 'AUTO_APPROVED')
					OR (c.participation_id = :viewer AND c.status <> 'FAILED'))
				ORDER BY c.submitted_at, c.id
				""")
				.param("challengeId", challengeId)
				.param("certDate", certDate)
				.param("viewer", viewerParticipationId)
				.param("viewerUser", viewerUserId)
				.query((rs, rowNum) -> {
					LocalDateTime submittedAt = rs.getObject("submitted_at", LocalDateTime.class);
					return new FeedRow(rs.getLong("id"), rs.getLong("participation_id"), rs.getString("status"),
							submittedAt == null ? null : submittedAt.toInstant(ZoneOffset.UTC), rs.getString("image_key"),
							rs.getInt("like_count"), rs.getBoolean("liked"));
				})
				.list();
	}
}

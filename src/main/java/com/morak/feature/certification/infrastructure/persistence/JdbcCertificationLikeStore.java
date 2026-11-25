package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.port.out.CertificationLikeStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 인증 좋아요 저장소. DB의 DATETIME은 UTC다. */
@Component
class JdbcCertificationLikeStore implements CertificationLikeStore {

	static final String LIKE = "LIKE";

	private final JdbcClient jdbc;

	JdbcCertificationLikeStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<String> findStatus(long challengeId, long certificationId) {
		return jdbc.sql("SELECT status FROM morak.certification WHERE id = :id AND challenge_id = :challengeId")
				.param("id", certificationId)
				.param("challengeId", challengeId)
				.query(String.class)
				.optional();
	}

	@Override
	public void like(long certificationId, long userId, Instant likedAt) {
		jdbc.sql("""
				INSERT INTO morak.certification_reaction (certification_id, user_id, reaction_type, created_at)
				VALUES (:certificationId, :userId, :type, :likedAt)
				ON CONFLICT (certification_id, user_id) DO NOTHING
				""")
				.param("certificationId", certificationId)
				.param("userId", userId)
				.param("type", LIKE)
				.param("likedAt", LocalDateTime.ofInstant(likedAt, ZoneOffset.UTC))
				.update();
	}

	@Override
	public void unlike(long certificationId, long userId) {
		jdbc.sql("""
				DELETE FROM morak.certification_reaction
				WHERE certification_id = :certificationId AND user_id = :userId AND reaction_type = :type
				""")
				.param("certificationId", certificationId)
				.param("userId", userId)
				.param("type", LIKE)
				.update();
	}

	@Override
	public int count(long certificationId) {
		return jdbc.sql("""
				SELECT COUNT(*) FROM morak.certification_reaction WHERE certification_id = :id AND reaction_type = :type
				""")
				.param("id", certificationId)
				.param("type", LIKE)
				.query(Integer.class)
				.single();
	}
}

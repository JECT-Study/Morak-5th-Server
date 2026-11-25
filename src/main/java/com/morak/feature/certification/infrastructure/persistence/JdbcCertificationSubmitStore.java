package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.port.out.CertificationSubmitStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 인증 제출 저장소. 인증 엔티티가 생기기 전까지 다른 인증 저장소처럼 JDBC로 쓴다. DB의 DATETIME은 UTC다. */
@Component
class JdbcCertificationSubmitStore implements CertificationSubmitStore {

	private final JdbcClient jdbc;

	JdbcCertificationSubmitStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean exists(long participationId, LocalDate certDate) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM morak.certification WHERE participation_id = :p AND cert_date = :d)")
				.param("p", participationId)
				.param("d", certDate)
				.query(Boolean.class)
				.single();
	}

	@Override
	@Transactional
	public Optional<Long> save(long challengeId, long participationId, LocalDate certDate, Instant submittedAt,
			Instant judgeDueAt, String imageKey) {
		// 동시 제출은 uk_certification_participation_date가 막는다. 먼저 들어간 한 건만 남는다.
		Optional<Long> id = jdbc.sql("""
				INSERT INTO morak.certification
					(challenge_id, participation_id, cert_date, status, submitted_at, judge_due_at, updated_at)
				VALUES (:challengeId, :participationId, :certDate, 'PENDING', :submittedAt, :judgeDueAt, :submittedAt)
				ON CONFLICT (participation_id, cert_date) DO NOTHING
				RETURNING id
				""")
				.param("challengeId", challengeId)
				.param("participationId", participationId)
				.param("certDate", certDate)
				.param("submittedAt", utc(submittedAt))
				.param("judgeDueAt", judgeDueAt == null ? null : utc(judgeDueAt), java.sql.Types.TIMESTAMP)
				.query(Long.class)
				.optional();
		id.ifPresent(certificationId -> jdbc.sql("""
				INSERT INTO morak.certification_image (certification_id, image_key, sort_order) VALUES (:id, :key, 0)
				""")
				.param("id", certificationId)
				.param("key", imageKey)
				.update());
		return id;
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}

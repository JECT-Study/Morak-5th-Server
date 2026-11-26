package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.port.out.CertificationFailureStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 미제출 FAILED 저장. 한 문장이라 따로 트랜잭션을 걸지 않는다. DB의 DATETIME은 UTC다. */
@Component
class JdbcCertificationFailureStore implements CertificationFailureStore {

	private final JdbcClient jdbc;

	JdbcCertificationFailureStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public int insertMissing(LocalDate lastDate, Instant now) {
		// 퇴장자(EXPELLED)·조기 종료 남은 두 사람(EARLY_ENDED)은 ACTIVE가 아니라 빠진다. 같은 날짜 제출과의 경합은 유니크 제약이 막는다.
		return jdbc.sql("""
				INSERT INTO morak.certification
					(challenge_id, participation_id, cert_date, status, submitted_at, judge_due_at, updated_at)
				SELECT ch.id, p.id, d.day::date, 'FAILED', NULL, NULL, :now
				FROM morak.challenge ch
				JOIN morak.participation p ON p.challenge_id = ch.id AND p.status = 'ACTIVE'
				CROSS JOIN LATERAL generate_series(ch.start_date, LEAST(ch.end_date, CAST(:lastDate AS date)),
					interval '1 day') AS d(day)
				WHERE ch.status IN ('IN_PROGRESS', 'FINALIZING') AND ch.deleted_at IS NULL
				ON CONFLICT (participation_id, cert_date) DO NOTHING
				""")
				.param("lastDate", lastDate)
				.param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
				.update();
	}
}

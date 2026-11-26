package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.port.out.CertificationExpelStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 퇴장 판정용 실패 횟수 집계. 잠그지 않고 읽으며, 퇴장 여부는 참가 도메인이 잠근 뒤 다시 확인한다. */
@Component
class JdbcCertificationExpelStore implements CertificationExpelStore {

	private static final String FAIL_COUNTS = """
			SELECT ch.id AS challenge_id, ch.title, ch.start_date, ch.end_date, ch.point_amount, p.id AS participation_id,
				COUNT(*) AS fail_count
			FROM morak.challenge ch
			JOIN morak.participation p ON p.challenge_id = ch.id AND p.status = 'ACTIVE'
			JOIN morak.certification c ON c.participation_id = p.id
				AND c.status IN ('FAILED', 'REJECTED') AND %s
			WHERE ch.status = 'IN_PROGRESS' AND ch.deleted_at IS NULL AND %s AND ch.end_date >= :today
			GROUP BY ch.id, ch.title, ch.start_date, ch.end_date, ch.point_amount, p.id
			ORDER BY ch.id, p.id
			""";

	private final JdbcClient jdbc;

	JdbcCertificationExpelStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<FailCount> findFailCounts(LocalDate today) {
		return jdbc.sql(FAIL_COUNTS.formatted("c.cert_date < :today", "ch.point_amount = 0"))
				.param("today", today)
				.query(JdbcCertificationExpelStore::failCount)
				.list();
	}

	@Override
	public List<FailCount> findReviewedFailCounts(long challengeId, LocalDate through, LocalDate today) {
		return jdbc.sql(FAIL_COUNTS.formatted("c.cert_date <= :through", "ch.point_amount > 0 AND ch.id = :id"))
				.param("through", through)
				.param("today", today)
				.param("id", challengeId)
				.query(JdbcCertificationExpelStore::failCount)
				.list();
	}

	@Override
	public Optional<LocalDate> findEarliestPendingDate(long challengeId) {
		// 퇴장자의 퇴장일 인증은 집계에서 빠지므로(D-12) 검수되지 않아도 판정을 막지 않는다.
		return jdbc.sql("""
				SELECT MIN(c.cert_date) FROM morak.certification c
				JOIN morak.participation p ON p.id = c.participation_id AND p.status = 'ACTIVE'
				WHERE c.challenge_id = :id AND c.status = 'PENDING'
				""").param("id", challengeId).query(LocalDate.class).optional();
	}

	private static FailCount failCount(ResultSet rs, int rowNum) throws SQLException {
		return new FailCount(rs.getLong("challenge_id"), rs.getString("title"),
				rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
				rs.getInt("point_amount"), rs.getLong("participation_id"), rs.getInt("fail_count"));
	}
}

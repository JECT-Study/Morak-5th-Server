package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.port.out.CertificationExpelStore;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 퇴장 판정용 실패 횟수 집계. 잠그지 않고 읽으며, 퇴장 여부는 참가 도메인이 잠근 뒤 다시 확인한다. */
@Component
class JdbcCertificationExpelStore implements CertificationExpelStore {

	private final JdbcClient jdbc;

	JdbcCertificationExpelStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<FailCount> findFailCounts(LocalDate today) {
		return jdbc.sql("""
				SELECT ch.id AS challenge_id, ch.title, ch.start_date, ch.end_date, p.id AS participation_id,
					COUNT(*) AS fail_count
				FROM morak.challenge ch
				JOIN morak.participation p ON p.challenge_id = ch.id AND p.status = 'ACTIVE'
				JOIN morak.certification c ON c.participation_id = p.id
					AND c.status IN ('FAILED', 'REJECTED') AND c.cert_date < :today
				WHERE ch.status = 'IN_PROGRESS' AND ch.deleted_at IS NULL AND ch.point_amount = 0
					AND ch.end_date >= :today
				GROUP BY ch.id, ch.title, ch.start_date, ch.end_date, p.id
				ORDER BY ch.id, p.id
				""")
				.param("today", today)
				.query((rs, rowNum) -> new FailCount(rs.getLong("challenge_id"), rs.getString("title"),
						rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
						rs.getLong("participation_id"), rs.getInt("fail_count")))
				.list();
	}
}

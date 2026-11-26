package com.morak.feature.challenge.infrastructure.persistence;

import com.morak.feature.challenge.application.query.ChallengeQuery;
import com.morak.feature.challenge.application.query.ChallengeQuery.CertificationWindow;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 챌린지 엔티티가 생기기 전까지의 조회 구현. 엔티티/리포지토리가 생기면 그쪽으로 교체한다. */
@Component
public class JdbcChallengeQuery implements ChallengeQuery {

	private final JdbcClient jdbc;

	public JdbcChallengeQuery(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<LocalDate> findEndDate(Long challengeId) {
		return jdbc.sql("SELECT end_date FROM morak.challenge WHERE id = :id")
				.param("id", challengeId)
				.query(LocalDate.class)
				.optional();
	}

	@Override
	public Optional<CertificationWindow> findCertificationWindow(Long challengeId) {
		return jdbc.sql("SELECT status, start_date, end_date, point_amount FROM morak.challenge WHERE id = :id")
				.param("id", challengeId)
				.query((rs, rowNum) -> new CertificationWindow(rs.getString("status"),
						rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
						rs.getInt("point_amount")))
				.optional();
	}

	@Override
	public Optional<String> findTitle(Long challengeId) {
		return jdbc.sql("SELECT title FROM morak.challenge WHERE id = :id")
				.param("id", challengeId)
				.query(String.class)
				.optional();
	}
}

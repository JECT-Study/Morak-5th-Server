package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.query.CertificationQuery;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 인증 엔티티가 생기기 전까지의 조회 구현. 엔티티/리포지토리가 생기면 그쪽으로 교체한다. */
@Component
public class JdbcCertificationQuery implements CertificationQuery {

	private final JdbcClient jdbc;

	public JdbcCertificationQuery(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<Long, CertificationInfo> findInChallenge(Long challengeId, Collection<Long> certificationIds) {
		Map<Long, CertificationInfo> result = new HashMap<>();
		if (certificationIds.isEmpty()) {
			return result;
		}
		jdbc.sql("""
				SELECT id, participation_id, cert_date FROM morak.certification
				WHERE challenge_id = :challengeId AND id IN (:ids)
				""")
				.param("challengeId", challengeId)
				.param("ids", certificationIds)
				.query((rs, rowNum) -> {
					CertificationInfo info = new CertificationInfo(
							rs.getLong("id"), rs.getLong("participation_id"), rs.getObject("cert_date", LocalDate.class));
					result.put(info.certificationId(), info);
					return info;
				})
				.list();
		return result;
	}

	@Override
	public Map<Long, CertificationStats> findStatsByUserIds(Collection<Long> userIds) {
		Map<Long, CertificationStats> result = new HashMap<>();
		if (userIds.isEmpty()) {
			return result;
		}
		jdbc.sql("""
				SELECT p.user_id,
					COUNT(*) FILTER (WHERE c.status IN ('APPROVED', 'AUTO_APPROVED')) AS approved_count,
					COUNT(*) AS obligation_count
				FROM morak.certification c
				JOIN morak.participation p ON p.id = c.participation_id
				WHERE p.user_id IN (:userIds)
				AND c.status IN ('APPROVED', 'AUTO_APPROVED', 'REJECTED', 'FAILED')
				GROUP BY p.user_id
				""")
				.param("userIds", userIds)
				.query((rs, rowNum) -> {
					result.put(
							rs.getLong("user_id"),
							new CertificationStats(rs.getInt("approved_count"), rs.getInt("obligation_count")));
					return rowNum;
				})
				.list();
		return result;
	}
}

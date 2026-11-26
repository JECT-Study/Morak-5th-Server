package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.port.out.CertificationFinalizeStore;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 결과 확정용 읽기. 다른 인증 저장소처럼 참가·챌린지 테이블은 읽기만 한다. DB의 DATETIME은 UTC다. */
@Component
class JdbcCertificationFinalizeStore implements CertificationFinalizeStore {

	private final JdbcClient jdbc;

	JdbcCertificationFinalizeStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Long> findCandidates(LocalDate today) {
		// 포인트 챌린지는 정산(§4.5)과 같은 트랜잭션에서 확정해야 해서 포인트 담당 작업에서 다룬다(point_amount = 0만).
		return jdbc.sql("""
				SELECT id FROM morak.challenge
				WHERE deleted_at IS NULL AND point_amount = 0
					AND ((status = 'IN_PROGRESS' AND end_date < :today) OR status = 'FINALIZING')
				ORDER BY id
				""")
				.param("today", today)
				.query(Long.class)
				.list();
	}

	@Override
	public List<Member> findMembers(long challengeId) {
		// 마지막 의무일은 홈 카드(JdbcCertificationCardStore, D-44)와 같다. LEAST는 NULL(끝나지 않은 참가자)을 무시한다.
		List<Row> rows = jdbc.sql("""
				SELECT p.id, p.status = 'ACTIVE' AS active, ch.start_date,
					LEAST(ch.end_date,
						CAST(p.ended_at AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Seoul' AS date) - 1) AS last_day
				FROM morak.participation p
				JOIN morak.challenge ch ON ch.id = p.challenge_id
				WHERE p.challenge_id = :challengeId AND p.status IN ('ACTIVE', 'COMPLETED', 'EXPELLED', 'EARLY_ENDED')
				ORDER BY p.id
				""")
				.param("challengeId", challengeId)
				.query((rs, rowNum) -> new Row(rs.getLong("id"), rs.getBoolean("active"),
						rs.getObject("start_date", LocalDate.class), rs.getObject("last_day", LocalDate.class)))
				.list();
		Map<Long, Row> byId = new HashMap<>();
		rows.forEach(row -> byId.put(row.id(), row));
		Map<Long, Set<LocalDate>> success = new HashMap<>();
		Map<Long, Integer> pending = new HashMap<>();
		jdbc.sql("""
				SELECT participation_id, cert_date, status FROM morak.certification
				WHERE challenge_id = :challengeId AND status IN ('APPROVED', 'AUTO_APPROVED', 'PENDING')
				""")
				.param("challengeId", challengeId)
				.query(rs -> {
					Row row = byId.get(rs.getLong("participation_id"));
					LocalDate day = rs.getObject("cert_date", LocalDate.class);
					// 의무 기간 밖(퇴장일·조기 종료일 제출 등, D-12)은 세지 않는다.
					if (row == null || day.isBefore(row.startDate()) || day.isAfter(row.lastDay())) {
						return;
					}
					if ("PENDING".equals(rs.getString("status"))) {
						pending.merge(row.id(), 1, Integer::sum);
					} else {
						success.computeIfAbsent(row.id(), id -> new HashSet<>()).add(day);
					}
				});
		return rows.stream()
				.map(row -> new Member(row.id(), row.active(), row.startDate(), row.lastDay(),
						success.getOrDefault(row.id(), Set.of()), pending.getOrDefault(row.id(), 0)))
				.toList();
	}

	private record Row(long id, boolean active, LocalDate startDate, LocalDate lastDay) {}
}

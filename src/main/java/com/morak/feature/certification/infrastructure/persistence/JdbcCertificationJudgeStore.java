package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.port.out.CertificationJudgeStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 방장·부방장 판정 저장소. 판정 대상은 {@code judge_due_at}이 있는 인증(포인트 없는 챌린지, D-34 ⑤)이고, 마감 전에만 판정할 수 있다.
 * DB의 DATETIME은 UTC다.
 */
@Component
class JdbcCertificationJudgeStore implements CertificationJudgeStore {

	private final JdbcClient jdbc;

	JdbcCertificationJudgeStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<PendingRow> findPending(long challengeId, Instant now) {
		return jdbc.sql("""
				SELECT c.id, c.participation_id, c.cert_date, c.submitted_at, c.judge_due_at, ci.image_key
				FROM morak.certification c
				LEFT JOIN morak.certification_image ci ON ci.certification_id = c.id AND ci.sort_order = 0
				WHERE c.challenge_id = :challengeId AND c.status = 'PENDING' AND c.judge_due_at > :now
				ORDER BY c.submitted_at, c.id
				""")
				.param("challengeId", challengeId)
				.param("now", utc(now))
				.query((rs, rowNum) -> new PendingRow(rs.getLong("id"), rs.getLong("participation_id"),
						rs.getObject("cert_date", LocalDate.class), instant(rs, "submitted_at"),
						instant(rs, "judge_due_at"), rs.getString("image_key")))
				.list();
	}

	@Override
	public Optional<JudgeRow> findById(long certificationId) {
		return jdbc.sql("""
				SELECT id, challenge_id, participation_id, cert_date, status, judge_due_at
				FROM morak.certification WHERE id = :id
				""")
				.param("id", certificationId)
				.query((rs, rowNum) -> new JudgeRow(rs.getLong("id"), rs.getLong("challenge_id"),
						rs.getLong("participation_id"), rs.getObject("cert_date", LocalDate.class), rs.getString("status"),
						instant(rs, "judge_due_at")))
				.optional();
	}

	@Override
	public boolean judge(long certificationId, String newStatus, long judgedBy, String rejectReason, Instant now) {
		return jdbc.sql("""
				UPDATE morak.certification
				SET status = :status, judged_by = :judgedBy, judged_at = :now, reject_reason = :reason, updated_at = :now
				WHERE id = :id AND status = 'PENDING' AND judge_due_at > :now
				""")
				.param("status", newStatus)
				.param("judgedBy", judgedBy)
				.param("now", utc(now))
				.param("reason", rejectReason, java.sql.Types.VARCHAR)
				.param("id", certificationId)
				.update() == 1;
	}

	@Override
	public int countFailures(long participationId) {
		return jdbc.sql("""
				SELECT COUNT(*) FROM morak.certification
				WHERE participation_id = :participationId AND status IN ('REJECTED', 'FAILED')
				""")
				.param("participationId", participationId)
				.query(Integer.class)
				.single();
	}

	@Override
	public List<Long> findChallengesWithOverdue(Instant now) {
		return jdbc.sql("""
				SELECT DISTINCT challenge_id FROM morak.certification
				WHERE status = 'PENDING' AND judge_due_at <= :now
				ORDER BY challenge_id
				""")
				.param("now", utc(now))
				.query(Long.class)
				.list();
	}

	@Override
	public List<ApprovedDay> autoApprove(long challengeId, Instant now) {
		// 판정자는 없고(judged_by NULL) 처리 시각만 남긴다. 상태 조건부라 다시 돌려도 바뀌는 행이 없다.
		return jdbc.sql("""
				UPDATE morak.certification SET status = 'AUTO_APPROVED', judged_at = :now, updated_at = :now
				WHERE challenge_id = :challengeId AND status = 'PENDING' AND judge_due_at <= :now
				RETURNING participation_id, cert_date
				""")
				.param("challengeId", challengeId)
				.param("now", utc(now))
				.query((rs, rowNum) -> approvedDay(rs))
				.list();
	}

	@Override
	public List<ApprovedDay> findAutoApproved(long challengeId) {
		return jdbc.sql("""
				SELECT participation_id, cert_date FROM morak.certification
				WHERE challenge_id = :challengeId AND status = 'AUTO_APPROVED'
				""")
				.param("challengeId", challengeId)
				.query((rs, rowNum) -> approvedDay(rs))
				.list();
	}

	private static ApprovedDay approvedDay(ResultSet rs) throws SQLException {
		return new ApprovedDay(rs.getLong("participation_id"), rs.getObject("cert_date", LocalDate.class));
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		LocalDateTime value = rs.getObject(column, LocalDateTime.class);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}
}

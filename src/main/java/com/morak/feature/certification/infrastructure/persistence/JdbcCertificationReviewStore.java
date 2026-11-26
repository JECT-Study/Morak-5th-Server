package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.dto.result.CertificationReviewView.ChallengeSummary;
import com.morak.feature.certification.application.dto.result.CertificationReviewView.Item;
import com.morak.feature.certification.application.port.out.CertificationReviewStore;
import com.morak.feature.certification.domain.vo.ReviewDeadline;
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
 * 운영진 검수 저장소. 인증 엔티티가 생기기 전까지는 {@link JdbcCertificationQuery}처럼 JDBC로 읽고 쓴다.
 * 검수 대상은 {@code challenge.point_amount > 0}인 챌린지의 인증이다(위키 §4.4 판정 담당). DB의 DATETIME은 UTC다.
 */
@Component
class JdbcCertificationReviewStore implements CertificationReviewStore {

	private static final String REVIEWABLE = "ch.point_amount > 0";
	/** 검수 마감 = 인증일 다음 날 12:00 KST = 03:00 UTC. ReviewDeadline과 같은 값이다. */
	private static final String OVERDUE = "c.status = 'PENDING' AND ((c.cert_date + 1)::timestamp + interval '3 hours') < :now";

	private final JdbcClient jdbc;

	JdbcCertificationReviewStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Item> find(Filter filter, int limit) {
		StringBuilder sql = new StringBuilder("""
				SELECT c.id, c.challenge_id, ch.title, u.nickname, c.cert_date, c.submitted_at, c.status
				FROM morak.certification c
				JOIN morak.challenge ch ON ch.id = c.challenge_id
				JOIN morak.participation p ON p.id = c.participation_id
				LEFT JOIN morak.users u ON u.id = p.user_id
				""");
		var spec = jdbc.sql(whereOf(sql, filter).append("ORDER BY c.submitted_at, c.id LIMIT :limit").toString());
		return bind(spec, filter).param("limit", limit)
				.query((rs, rowNum) -> new Item(
						rs.getLong("id"), rs.getLong("challenge_id"), rs.getString("title"), rs.getString("nickname"),
						rs.getObject("cert_date", LocalDate.class), instant(rs, "submitted_at"), rs.getString("status"),
						ReviewDeadline.of(rs.getObject("cert_date", LocalDate.class))))
				.list();
	}

	@Override
	public int count(Filter filter) {
		StringBuilder sql = new StringBuilder("""
				SELECT COUNT(*) FROM morak.certification c
				JOIN morak.challenge ch ON ch.id = c.challenge_id
				""");
		var spec = jdbc.sql(whereOf(sql, filter).toString());
		return bind(spec, filter).query(Integer.class).single();
	}

	@Override
	public int countOverdue(Instant now) {
		return jdbc.sql("""
				SELECT COUNT(*) FROM morak.certification c
				JOIN morak.challenge ch ON ch.id = c.challenge_id
				WHERE %s AND %s
				""".formatted(REVIEWABLE, OVERDUE))
				.param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
				.query(Integer.class)
				.single();
	}

	@Override
	public List<ChallengeSummary> findChallengeSummaries(Instant now) {
		return jdbc.sql("""
				SELECT ch.id, ch.title,
					COUNT(*) FILTER (WHERE c.status = 'PENDING') AS pending,
					COUNT(*) FILTER (WHERE c.status IN ('APPROVED', 'AUTO_APPROVED')) AS approved,
					COUNT(*) FILTER (WHERE c.status = 'REJECTED') AS rejected,
					COUNT(*) FILTER (WHERE %s) AS overdue
				FROM morak.challenge ch
				JOIN morak.certification c ON c.challenge_id = ch.id
					AND c.status IN ('PENDING', 'APPROVED', 'AUTO_APPROVED', 'REJECTED')
				WHERE %s
				GROUP BY ch.id, ch.title
				ORDER BY ch.title, ch.id
				""".formatted(OVERDUE, REVIEWABLE))
				.param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
				.query((rs, rowNum) -> new ChallengeSummary(
						rs.getLong("id"), rs.getString("title"), rs.getInt("pending"), rs.getInt("approved"),
						rs.getInt("rejected"), rs.getInt("overdue")))
				.list();
	}

	@Override
	public Optional<Row> findById(long certificationId) {
		Optional<Row> row = jdbc.sql("""
				SELECT c.id, c.challenge_id, ch.title, ch.common_mission, ch.verification_rules,
					u.nickname, p.role, c.cert_date, c.submitted_at, c.status, c.reject_reason, c.judged_at,
					j.nickname AS judge_nickname
				FROM morak.certification c
				JOIN morak.challenge ch ON ch.id = c.challenge_id
				JOIN morak.participation p ON p.id = c.participation_id
				LEFT JOIN morak.users u ON u.id = p.user_id
				LEFT JOIN morak.users j ON j.id = c.judged_by
				WHERE c.id = :id AND %s
				""".formatted(REVIEWABLE))
				.param("id", certificationId)
				.query((rs, rowNum) -> new Row(
						rs.getLong("id"), rs.getLong("challenge_id"), rs.getString("title"), rs.getString("nickname"),
						rs.getString("role"), rs.getObject("cert_date", LocalDate.class), instant(rs, "submitted_at"),
						rs.getString("status"), rs.getString("common_mission"), rs.getString("verification_rules"),
						List.of(), rs.getString("judge_nickname"), instant(rs, "judged_at"), rs.getString("reject_reason")))
				.optional();
		return row.map(r -> withImageKeys(r, imageKeys(certificationId)));
	}

	@Override
	public boolean judge(long certificationId, String newStatus, long judgedBy, String rejectReason, Instant judgedAt) {
		LocalDateTime now = LocalDateTime.ofInstant(judgedAt, ZoneOffset.UTC);
		int updated = jdbc.sql("""
				UPDATE morak.certification c
				SET status = :status, judged_by = :judgedBy, judged_at = :now, reject_reason = :reason, updated_at = :now
				WHERE c.id = :id AND c.status = 'PENDING'
				AND EXISTS (SELECT 1 FROM morak.challenge ch WHERE ch.id = c.challenge_id AND ch.point_amount > 0)
				""")
				.param("status", newStatus)
				.param("judgedBy", judgedBy)
				.param("now", now)
				.param("reason", rejectReason, java.sql.Types.VARCHAR)
				.param("id", certificationId)
				.update();
		return updated == 1;
	}

	private List<String> imageKeys(long certificationId) {
		return jdbc.sql("SELECT image_key FROM morak.certification_image WHERE certification_id = :id ORDER BY sort_order, id")
				.param("id", certificationId)
				.query(String.class)
				.list();
	}

	private static Row withImageKeys(Row r, List<String> keys) {
		return new Row(r.certificationId(), r.challengeId(), r.challengeTitle(), r.submitterNickname(),
				r.submitterRole(), r.certDate(), r.submittedAt(), r.status(), r.commonMission(), r.verificationRules(),
				keys, r.judgeNickname(), r.judgedAt(), r.rejectReason());
	}

	private static StringBuilder whereOf(StringBuilder sql, Filter filter) {
		sql.append("WHERE ").append(REVIEWABLE).append(" AND c.status IN (:statuses) ");
		if (filter.certDate() != null) {
			sql.append("AND c.cert_date = :certDate ");
		}
		if (filter.challengeId() != null) {
			sql.append("AND c.challenge_id = :challengeId ");
		}
		return sql;
	}

	private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Filter filter) {
		spec = spec.param("statuses", filter.statuses());
		if (filter.certDate() != null) {
			spec = spec.param("certDate", filter.certDate());
		}
		if (filter.challengeId() != null) {
			spec = spec.param("challengeId", filter.challengeId());
		}
		return spec;
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		LocalDateTime value = rs.getObject(column, LocalDateTime.class);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}
}

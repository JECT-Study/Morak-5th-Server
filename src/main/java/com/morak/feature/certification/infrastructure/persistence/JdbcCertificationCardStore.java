package com.morak.feature.certification.infrastructure.persistence;

import com.morak.feature.certification.application.port.out.CertificationCardStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 홈 카드 인증 집계. 다른 인증 저장소처럼 참가·챌린지 테이블은 읽기만 한다. DB의 DATETIME은 UTC다. */
@Component
class JdbcCertificationCardStore implements CertificationCardStore {

	private final JdbcClient jdbc;

	JdbcCertificationCardStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<CardRow> findCards(Collection<Long> participationIds, LocalDate today, Instant now) {
		// 팀원마다 의무 마지막 날 = min(종료일, 어제, 끝난 날(KST) - 1). 퇴장·조기 종료한 날부터는 분모·분자에서 뺀다(위키 §4.4, §4.5).
		// LEAST는 NULL을 무시하므로 끝나지 않은 팀원(ended_at NULL)은 종료일·어제만 본다.
		// 판정 대기 수는 판정 목록(JdbcCertificationJudgeStore, CertificationJudge)과 같은 기준이다: 마감 전, 퇴장자 제외,
		// 방장은 방장 아닌 팀원의 인증, 부방장은 방장의 인증.
		return jdbc.sql("""
				SELECT p.id, p.challenge_id, r.target, COALESCE(r.failed, 0) AS failed,
					(ch.status = 'IN_PROGRESS' AND CAST(:today AS date) BETWEEN ch.start_date AND ch.end_date) AS today_open,
					(SELECT COUNT(*) FROM morak.participation t
						WHERE t.challenge_id = p.challenge_id AND t.status = 'ACTIVE') AS today_target,
					(SELECT COUNT(*) FROM morak.certification c JOIN morak.participation t ON t.id = c.participation_id
						WHERE c.challenge_id = p.challenge_id AND c.cert_date = :today AND t.status = 'ACTIVE'
						AND c.submitted_at IS NOT NULL) AS today_certified,
					(SELECT c.status FROM morak.certification c
						WHERE c.participation_id = p.id AND c.cert_date = :today) AS my_today_status,
					CASE
						WHEN p.status <> 'ACTIVE' OR ch.point_amount > 0 OR p.role NOT IN ('HOST', 'DEPUTY') THEN 0
						ELSE (SELECT COUNT(*) FROM morak.certification c
							JOIN morak.participation t ON t.id = c.participation_id
							WHERE c.challenge_id = p.challenge_id AND c.status = 'PENDING' AND c.judge_due_at > :now
							AND t.status <> 'EXPELLED' AND (t.role = 'HOST') = (p.role = 'DEPUTY'))
					END AS pending_judge
				FROM morak.participation p
				JOIN morak.challenge ch ON ch.id = p.challenge_id
				LEFT JOIN LATERAL (
					SELECT SUM(GREATEST(0, d.last_day - ch.start_date + 1)) AS target,
						SUM((SELECT COUNT(*) FROM morak.certification c
							WHERE c.participation_id = d.id AND c.status IN ('FAILED', 'REJECTED')
							AND c.cert_date BETWEEN ch.start_date AND d.last_day)) AS failed
					FROM (
						SELECT t.id, LEAST(ch.end_date, CAST(:yesterday AS date),
							CAST(t.ended_at AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Seoul' AS date) - 1) AS last_day
						FROM morak.participation t
						WHERE t.challenge_id = p.challenge_id
						AND t.status IN ('ACTIVE', 'EXPEL_NOTICED', 'EXPELLED', 'COMPLETED', 'EARLY_ENDED')
					) d
				) r ON ch.status IN ('IN_PROGRESS', 'FINALIZING', 'ENDED')
				WHERE p.id IN (:ids)
				""")
				.param("ids", participationIds)
				.param("today", today)
				.param("yesterday", today.minusDays(1))
				.param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
				.query((rs, rowNum) -> new CardRow(rs.getLong("id"), rs.getLong("challenge_id"),
						rs.getObject("target") == null ? null : rs.getLong("target"), rs.getLong("failed"),
						rs.getBoolean("today_open"), rs.getInt("today_target"), rs.getInt("today_certified"),
						rs.getString("my_today_status"), rs.getInt("pending_judge")))
				.list();
	}

	@Override
	public List<PhotoRow> findApprovedPhotos(Collection<Long> challengeIds, LocalDate today) {
		return jdbc.sql("""
				SELECT c.challenge_id, p.user_id, ci.image_key, c.submitted_at
				FROM morak.certification c
				JOIN morak.participation p ON p.id = c.participation_id AND p.status = 'ACTIVE'
				LEFT JOIN morak.certification_image ci ON ci.certification_id = c.id AND ci.sort_order = 0
				WHERE c.challenge_id IN (:challengeIds) AND c.cert_date = :today
				AND c.status IN ('APPROVED', 'AUTO_APPROVED')
				ORDER BY c.challenge_id, c.submitted_at, c.id
				""")
				.param("challengeIds", challengeIds)
				.param("today", today)
				.query((rs, rowNum) -> new PhotoRow(rs.getLong("challenge_id"), rs.getLong("user_id"),
						rs.getString("image_key"),
						rs.getObject("submitted_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)))
				.list();
	}
}

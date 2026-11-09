package com.morak.feature.notification.infrastructure.persistence;

import com.morak.feature.notification.application.InvalidatedApplicationNotice;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInvalidatedApplicationNotice implements InvalidatedApplicationNotice {

	private final JdbcClient jdbc;

	JdbcInvalidatedApplicationNotice(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void send(long recipientId, long challengeId, long participationId, Instant now) {
		jdbc.sql("""
				INSERT INTO morak.notification
					(recipient_user_id, type, title, body, target_type, target_id, dedup_key, created_at)
				VALUES (:recipientId, 'PARTICIPATION_INVALIDATED', '참가 신청이 무효 처리됐어요',
						'신청자가 참여 가능한 챌린지 한도에 도달했어요.',
						'CHALLENGE', :challengeId, :dedupKey, :now)
				ON CONFLICT (dedup_key) DO NOTHING
				""")
				.param("recipientId", recipientId)
				.param("challengeId", challengeId)
				.param("dedupKey", "PARTICIPATION_INVALIDATED:" + participationId)
				.param("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
				.update();
	}
}

package com.morak.feature.notification.infrastructure.persistence;

import com.morak.feature.notification.domain.entity.PushToken;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPushTokenRepository extends JpaRepository<PushToken, Long> {

	/** 토큰이 이미 있으면 소유 구독만 지금 사용자의 것으로 바꾼다. 한 문장으로 처리해 동시 요청에도 안전하다. */
	@Modifying
	@Query(
			nativeQuery = true,
			value = """
					INSERT INTO {h-schema}push_token (subscription_id, channel, token, user_agent, created_at)
					VALUES (:subscriptionId, :channel, :token, :userAgent, :now)
					ON CONFLICT (token) DO UPDATE
					SET subscription_id = EXCLUDED.subscription_id,
						channel = EXCLUDED.channel,
						user_agent = EXCLUDED.user_agent
					""")
	int upsert(
			@Param("subscriptionId") Long subscriptionId,
			@Param("channel") String channel,
			@Param("token") String token,
			@Param("userAgent") String userAgent,
			@Param("now") LocalDateTime now);

	@Modifying
	@Query(
			nativeQuery = true,
			value = """
					DELETE FROM {h-schema}push_token
					WHERE token = :token
					AND subscription_id IN (SELECT id FROM {h-schema}push_subscription WHERE user_id = :userId)
					""")
	int deleteOwnedToken(@Param("userId") Long userId, @Param("token") String token);
}

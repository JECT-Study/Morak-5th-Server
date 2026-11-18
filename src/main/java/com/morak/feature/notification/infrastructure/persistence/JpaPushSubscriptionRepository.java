package com.morak.feature.notification.infrastructure.persistence;

import com.morak.feature.notification.domain.entity.PushSubscription;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

	Optional<PushSubscription> findByUserId(Long userId);

	/** 사용자의 구독이 이미 있으면 아무것도 하지 않는다. 동시에 호출해도 한 행만 만들어진다. */
	@Modifying
	@Query(
			nativeQuery = true,
			value = """
					INSERT INTO {h-schema}push_subscription (user_id, enabled, updated_at)
					VALUES (:userId, TRUE, :now)
					ON CONFLICT (user_id) DO NOTHING
					""")
	int insertIfAbsent(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}

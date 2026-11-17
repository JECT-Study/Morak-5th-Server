package com.morak.feature.notification.infrastructure.persistence;

import com.morak.feature.notification.domain.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationRepository extends JpaRepository<Notification, Long> {

	@Query("select n from Notification n where n.recipientUserId = :userId order by n.id desc")
	List<Notification> findLatest(@Param("userId") Long userId, Pageable pageable);

	@Query("select n from Notification n where n.recipientUserId = :userId and n.id < :beforeId order by n.id desc")
	List<Notification> findBefore(
			@Param("userId") Long userId, @Param("beforeId") Long beforeId, Pageable pageable);

	long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

	boolean existsByIdAndRecipientUserId(Long id, Long recipientUserId);

	@Modifying
	@Query("update Notification n set n.readAt = :readAt "
			+ "where n.id = :id and n.recipientUserId = :userId and n.readAt is null")
	int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

	@Modifying
	@Query("update Notification n set n.readAt = :readAt where n.recipientUserId = :userId and n.readAt is null")
	int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

	/** PostgreSQL에는 INSERT IGNORE가 없어 ON CONFLICT로 중복 키를 조용히 건너뛴다. 저장된 행 수(0 또는 1)를 돌려준다. */
	@Modifying
	@Query(
			nativeQuery = true,
			value = """
					INSERT INTO {h-schema}notification
						(recipient_user_id, type, title, body, target_type, target_id, dedup_key, created_at)
					VALUES
						(:recipientUserId, :type, :title, :body, :targetType, :targetId, :dedupKey, :createdAt)
					ON CONFLICT (dedup_key) DO NOTHING
					""")
	int insertIgnoringDuplicate(
			@Param("recipientUserId") Long recipientUserId,
			@Param("type") String type,
			@Param("title") String title,
			@Param("body") String body,
			@Param("targetType") String targetType,
			@Param("targetId") Long targetId,
			@Param("dedupKey") String dedupKey,
			@Param("createdAt") LocalDateTime createdAt);
}

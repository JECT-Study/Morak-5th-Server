package com.morak.feature.chat.infrastructure.persistence;

import com.morak.feature.chat.domain.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	@Query("select m from ChatMessage m where m.challengeId = :challengeId order by m.id desc")
	List<ChatMessage> findLatest(@Param("challengeId") Long challengeId, Pageable pageable);

	@Query("select m from ChatMessage m where m.challengeId = :challengeId and m.id < :beforeId order by m.id desc")
	List<ChatMessage> findBefore(
			@Param("challengeId") Long challengeId, @Param("beforeId") Long beforeId, Pageable pageable);

	@Query("select m from ChatMessage m where m.challengeId = :challengeId and m.id > :afterId order by m.id asc")
	List<ChatMessage> findAfter(
			@Param("challengeId") Long challengeId, @Param("afterId") Long afterId, Pageable pageable);
}

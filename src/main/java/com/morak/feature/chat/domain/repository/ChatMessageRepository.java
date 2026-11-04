package com.morak.feature.chat.domain.repository;

import com.morak.feature.chat.domain.entity.ChatMessage;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository {

	ChatMessage save(ChatMessage message);

	Optional<ChatMessage> findById(Long id);

	/** 최신 메시지부터 limit개 (id 내림차순). */
	List<ChatMessage> findLatest(Long challengeId, int limit);

	/** beforeId보다 오래된 메시지 limit개 (id 내림차순). */
	List<ChatMessage> findBefore(Long challengeId, Long beforeId, int limit);

	/** afterId보다 새로운 메시지 limit개 (id 오름차순). */
	List<ChatMessage> findAfter(Long challengeId, Long afterId, int limit);
}

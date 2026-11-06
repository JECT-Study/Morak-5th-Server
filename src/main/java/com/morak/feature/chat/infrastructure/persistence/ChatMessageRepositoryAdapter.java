package com.morak.feature.chat.infrastructure.persistence;

import com.morak.feature.chat.domain.entity.ChatMessage;
import com.morak.feature.chat.domain.repository.ChatMessageRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class ChatMessageRepositoryAdapter implements ChatMessageRepository {

	private final JpaChatMessageRepository jpaRepository;

	public ChatMessageRepositoryAdapter(JpaChatMessageRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public ChatMessage save(ChatMessage message) {
		return jpaRepository.save(message);
	}

	@Override
	public Optional<ChatMessage> findById(Long id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<ChatMessage> findByParticipationIdAndClientMessageId(Long participationId, UUID clientMessageId) {
		return jpaRepository.findByParticipationIdAndClientMessageId(participationId, clientMessageId);
	}

	@Override
	public List<ChatMessage> findLatest(Long challengeId, int limit) {
		return jpaRepository.findLatest(challengeId, PageRequest.of(0, limit));
	}

	@Override
	public List<ChatMessage> findBefore(Long challengeId, Long beforeId, int limit) {
		return jpaRepository.findBefore(challengeId, beforeId, PageRequest.of(0, limit));
	}

	@Override
	public List<ChatMessage> findAfter(Long challengeId, Long afterId, int limit) {
		return jpaRepository.findAfter(challengeId, afterId, PageRequest.of(0, limit));
	}
}

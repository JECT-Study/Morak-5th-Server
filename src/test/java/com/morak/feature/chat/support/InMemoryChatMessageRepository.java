package com.morak.feature.chat.support;

import com.morak.feature.chat.domain.entity.ChatMessage;
import com.morak.feature.chat.domain.repository.ChatMessageRepository;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** DB 없이 채팅 로직을 검증하기 위한 인메모리 저장소. id는 저장 순서대로 1부터 붙는다. */
public class InMemoryChatMessageRepository implements ChatMessageRepository {

	public final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
	private final AtomicLong sequence = new AtomicLong();

	@Override
	public ChatMessage save(ChatMessage message) {
		try {
			Field id = ChatMessage.class.getDeclaredField("id");
			id.setAccessible(true);
			id.set(message, sequence.incrementAndGet());
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
		messages.add(message);
		return message;
	}

	@Override
	public Optional<ChatMessage> findById(Long id) {
		return messages.stream().filter(m -> m.getId().equals(id)).findFirst();
	}

	@Override
	public Optional<ChatMessage> findByParticipationIdAndClientMessageId(Long participationId, UUID clientMessageId) {
		return messages.stream()
				.filter(m -> participationId.equals(m.getParticipationId())
						&& clientMessageId.equals(m.getClientMessageId()))
				.findFirst();
	}

	@Override
	public List<ChatMessage> findLatest(Long challengeId, int limit) {
		return messages.stream().sorted(Comparator.comparing(ChatMessage::getId).reversed()).limit(limit).toList();
	}

	@Override
	public List<ChatMessage> findBefore(Long challengeId, Long beforeId, int limit) {
		return messages.stream().filter(m -> m.getId() < beforeId)
				.sorted(Comparator.comparing(ChatMessage::getId).reversed()).limit(limit).toList();
	}

	@Override
	public List<ChatMessage> findAfter(Long challengeId, Long afterId, int limit) {
		return messages.stream().filter(m -> m.getId() > afterId)
				.sorted(Comparator.comparing(ChatMessage::getId)).limit(limit).toList();
	}
}

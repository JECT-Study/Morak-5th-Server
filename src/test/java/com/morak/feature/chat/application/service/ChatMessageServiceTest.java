package com.morak.feature.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.application.dto.ChatMessageResult;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.chat.domain.entity.ChatMessage;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import com.morak.feature.chat.domain.repository.ChatMessageRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatMessageServiceTest {

	private static final Long CHALLENGE = 1L;
	private static final LocalDate END_DATE = LocalDate.of(2026, 11, 10);

	private final FakeRepository repository = new FakeRepository();
	private final FakeReader reader = new FakeReader();

	private ChatMessageService service(String instant) {
		return new ChatMessageService(repository, reader, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
	}

	@Test
	void activeParticipantCanSendBeforeClose() {
		reader.participants.put(10L, new ChatParticipantReader.ChatParticipant(100L, 10L, "MEMBER", "ACTIVE"));

		ChatMessageResult result = service("2026-11-10T14:59:59Z").send(CHALLENGE, 10L, "안녕");

		assertEquals(10L, result.senderId());
		assertEquals("안녕", result.content());
	}

	@Test
	void sendIsRejectedFromMidnightKstAfterEndDate() {
		reader.participants.put(10L, new ChatParticipantReader.ChatParticipant(100L, 10L, "MEMBER", "ACTIVE"));

		// 2026-11-10T15:00:00Z == 2026-11-11 00:00 KST
		BusinessException exception = assertThrows(BusinessException.class,
				() -> service("2026-11-10T15:00:00Z").send(CHALLENGE, 10L, "안녕"));

		assertEquals(ChatErrorCode.CHAT_CLOSED, exception.getErrorCode());
	}

	@Test
	void completedParticipantCannotSendButCanRead() {
		reader.participants.put(10L, new ChatParticipantReader.ChatParticipant(100L, 10L, "MEMBER", "COMPLETED"));
		ChatMessageService service = service("2026-11-09T00:00:00Z");

		BusinessException exception = assertThrows(BusinessException.class, () -> service.send(CHALLENGE, 10L, "x"));
		assertEquals(ChatErrorCode.NOT_PARTICIPANT, exception.getErrorCode());
		assertTrue(service.list(CHALLENGE, 10L, null, null, null).isEmpty());
	}

	@Test
	void nonParticipantCannotReadOrSend() {
		ChatMessageService service = service("2026-11-09T00:00:00Z");

		assertEquals(ChatErrorCode.NOT_PARTICIPANT, assertThrows(BusinessException.class,
				() -> service.list(CHALLENGE, 99L, null, null, null)).getErrorCode());
		assertEquals(ChatErrorCode.NOT_PARTICIPANT, assertThrows(BusinessException.class,
				() -> service.send(CHALLENGE, 99L, "x")).getErrorCode());
	}

	@Test
	void blankOrTooLongContentIsRejected() {
		reader.participants.put(10L, new ChatParticipantReader.ChatParticipant(100L, 10L, "MEMBER", "ACTIVE"));
		ChatMessageService service = service("2026-11-09T00:00:00Z");

		assertEquals(ChatErrorCode.INVALID_MESSAGE, assertThrows(BusinessException.class,
				() -> service.send(CHALLENGE, 10L, "  ")).getErrorCode());
		assertEquals(ChatErrorCode.INVALID_MESSAGE, assertThrows(BusinessException.class,
				() -> service.send(CHALLENGE, 10L, "a".repeat(1001))).getErrorCode());
	}

	@Test
	void listReturnsAscendingWithCursors() {
		reader.participants.put(10L, new ChatParticipantReader.ChatParticipant(100L, 10L, "MEMBER", "ACTIVE"));
		ChatMessageService service = service("2026-11-09T00:00:00Z");
		for (int i = 1; i <= 5; i++) {
			service.send(CHALLENGE, 10L, "m" + i);
		}

		List<ChatMessageResult> latest = service.list(CHALLENGE, 10L, null, null, 3);
		assertEquals(List.of("m3", "m4", "m5"), latest.stream().map(ChatMessageResult::content).toList());

		Long firstId = latest.get(0).id();
		List<ChatMessageResult> older = service.list(CHALLENGE, 10L, firstId, null, 10);
		assertEquals(List.of("m1", "m2"), older.stream().map(ChatMessageResult::content).toList());

		List<ChatMessageResult> newer = service.list(CHALLENGE, 10L, null, firstId, 10);
		assertEquals(List.of("m4", "m5"), newer.stream().map(ChatMessageResult::content).toList());

		assertEquals(ChatErrorCode.INVALID_CURSOR, assertThrows(BusinessException.class,
				() -> service.list(CHALLENGE, 10L, 3L, 1L, null)).getErrorCode());
	}

	@Test
	void deletedMessageHidesContent() throws Exception {
		reader.participants.put(10L, new ChatParticipantReader.ChatParticipant(100L, 10L, "MEMBER", "ACTIVE"));
		ChatMessageService service = service("2026-11-09T00:00:00Z");
		service.send(CHALLENGE, 10L, "secret");
		ChatMessage stored = repository.messages.get(0);
		Field deletedAt = ChatMessage.class.getDeclaredField("deletedAt");
		deletedAt.setAccessible(true);
		deletedAt.set(stored, LocalDateTime.now());

		ChatMessageResult result = service.list(CHALLENGE, 10L, null, null, null).get(0);

		assertTrue(result.deleted());
		assertNull(result.content());
	}

	@Test
	void deleteIsNotImplementedYet() {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> service("2026-11-09T00:00:00Z").delete(1L, 10L));
		assertEquals(ChatErrorCode.NOT_IMPLEMENTED, exception.getErrorCode());
	}

	private static class FakeReader implements ChatParticipantReader {

		final Map<Long, ChatParticipant> participants = new HashMap<>();

		@Override
		public Optional<ChatParticipant> findChatParticipant(Long challengeId, Long userId) {
			return Optional.ofNullable(participants.get(userId));
		}

		@Override
		public Map<Long, Long> findUserIdsByParticipationIds(Collection<Long> participationIds) {
			Map<Long, Long> result = new HashMap<>();
			participants.values().forEach(p -> {
				if (participationIds.contains(p.participationId())) {
					result.put(p.participationId(), p.userId());
				}
			});
			return result;
		}

		@Override
		public Optional<LocalDate> findChallengeEndDate(Long challengeId) {
			return Optional.of(END_DATE);
		}
	}

	private static class FakeRepository implements ChatMessageRepository {

		final List<ChatMessage> messages = new ArrayList<>();
		private long sequence = 0;

		@Override
		public ChatMessage save(ChatMessage message) {
			try {
				Field id = ChatMessage.class.getDeclaredField("id");
				id.setAccessible(true);
				id.set(message, ++sequence);
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
}

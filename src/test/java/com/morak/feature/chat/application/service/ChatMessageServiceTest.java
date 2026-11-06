package com.morak.feature.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.application.dto.ChatMessageResult;
import com.morak.feature.chat.application.port.out.ChatCertificationReader;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.chat.domain.entity.ChatMessage;
import com.morak.feature.chat.domain.entity.ChatMessageType;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import com.morak.feature.chat.support.InMemoryChatMessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatMessageServiceTest {

	private static final Long CHALLENGE = 1L;
	private static final LocalDate END_DATE = LocalDate.of(2026, 11, 10);

	private final InMemoryChatMessageRepository repository = new InMemoryChatMessageRepository();
	private final FakeReader reader = new FakeReader();
	private final FakeCertificationReader certificationReader = new FakeCertificationReader();

	private ChatMessageService service(String instant) {
		return new ChatMessageService(
				repository, reader, certificationReader, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
	}

	private ChatMessageResult send(ChatMessageService service, Long userId, String content) {
		return service.send(CHALLENGE, userId, UUID.randomUUID(), content, null);
	}

	@Test
	void activeParticipantCanSendBeforeClose() {
		reader.addParticipant(10L, 100L, "HOST", "ACTIVE", "모락");
		UUID clientMessageId = UUID.randomUUID();

		ChatMessageResult result = service("2026-11-10T14:59:59Z").send(CHALLENGE, 10L, clientMessageId, "안녕", null);

		assertEquals(ChatMessageType.USER, result.type());
		assertEquals(clientMessageId, result.clientMessageId());
		assertEquals("안녕", result.content());
		assertEquals(10L, result.sender().userId());
		assertEquals("모락", result.sender().nickname());
		assertEquals("HOST", result.sender().role());
		assertNull(result.reply());
	}

	@Test
	void createdAtIsStoredAndReturnedInUtc() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");

		ChatMessageResult result = send(service("2026-11-05T06:00:00Z"), 10L, "안녕");

		assertEquals(Instant.parse("2026-11-05T06:00:00Z"), result.createdAt());
		assertEquals(LocalDateTime.of(2026, 11, 5, 6, 0), repository.messages.get(0).getCreatedAt());
	}

	@Test
	void sendIsRejectedFromMidnightKstAfterEndDate() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");

		// 2026-11-10T15:00:00Z == 2026-11-11 00:00 KST
		BusinessException exception = assertThrows(BusinessException.class,
				() -> send(service("2026-11-10T15:00:00Z"), 10L, "안녕"));

		assertEquals(ChatErrorCode.CHAT_CLOSED, exception.getErrorCode());
	}

	@Test
	void completedAndExpelNoticedParticipantsCanReadButNotSend() {
		reader.addParticipant(10L, 100L, "MEMBER", "COMPLETED", "완주자");
		reader.addParticipant(11L, 101L, "MEMBER", "EXPEL_NOTICED", "퇴장예정");
		ChatMessageService service = service("2026-11-09T00:00:00Z");

		for (Long userId : List.of(10L, 11L)) {
			BusinessException exception = assertThrows(BusinessException.class, () -> send(service, userId, "x"));
			assertEquals(ChatErrorCode.SEND_FORBIDDEN, exception.getErrorCode());
			assertTrue(service.list(CHALLENGE, userId, null, null, null).isEmpty());
		}
	}

	@Test
	void nonParticipantCannotReadOrSend() {
		ChatMessageService service = service("2026-11-09T00:00:00Z");

		assertEquals(ChatErrorCode.NOT_PARTICIPANT, assertThrows(BusinessException.class,
				() -> service.list(CHALLENGE, 99L, null, null, null)).getErrorCode());
		assertEquals(ChatErrorCode.NOT_PARTICIPANT, assertThrows(BusinessException.class,
				() -> send(service, 99L, "x")).getErrorCode());
	}

	@Test
	void blankOrTooLongContentIsRejected() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");
		ChatMessageService service = service("2026-11-09T00:00:00Z");

		assertEquals(ChatErrorCode.INVALID_MESSAGE, assertThrows(BusinessException.class,
				() -> send(service, 10L, "  ")).getErrorCode());
		assertEquals(ChatErrorCode.INVALID_MESSAGE, assertThrows(BusinessException.class,
				() -> send(service, 10L, "a".repeat(1001))).getErrorCode());
		assertEquals(1000, send(service, 10L, "a".repeat(1000)).content().length());
	}

	@Test
	void missingClientMessageIdIsRejected() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");

		BusinessException exception = assertThrows(BusinessException.class,
				() -> service("2026-11-09T00:00:00Z").send(CHALLENGE, 10L, null, "안녕", null));

		assertEquals(ChatErrorCode.INVALID_CLIENT_MESSAGE_ID, exception.getErrorCode());
	}

	@Test
	void resendWithSameClientMessageIdDoesNotCreateDuplicate() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");
		ChatMessageService service = service("2026-11-09T00:00:00Z");
		UUID clientMessageId = UUID.randomUUID();

		ChatMessageResult first = service.send(CHALLENGE, 10L, clientMessageId, "처음", null);
		ChatMessageResult resent = service.send(CHALLENGE, 10L, clientMessageId, "내용을 바꿔서 재전송", null);

		assertEquals(1, repository.messages.size());
		assertEquals(first.id(), resent.id());
		assertEquals("처음", resent.content());
	}

	@Test
	void sameClientMessageIdFromAnotherParticipantIsAnotherMessage() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");
		reader.addParticipant(11L, 101L, "MEMBER", "ACTIVE", "다른사람");
		ChatMessageService service = service("2026-11-09T00:00:00Z");
		UUID clientMessageId = UUID.randomUUID();

		service.send(CHALLENGE, 10L, clientMessageId, "a", null);
		service.send(CHALLENGE, 11L, clientMessageId, "b", null);

		assertEquals(2, repository.messages.size());
	}

	@Test
	void resendAfterCloseIsRejectedEvenIfAlreadyStored() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");
		UUID clientMessageId = UUID.randomUUID();
		service("2026-11-10T14:59:59Z").send(CHALLENGE, 10L, clientMessageId, "처음", null);

		BusinessException exception = assertThrows(BusinessException.class,
				() -> service("2026-11-10T15:00:00Z").send(CHALLENGE, 10L, clientMessageId, "처음", null));

		assertEquals(ChatErrorCode.CHAT_CLOSED, exception.getErrorCode());
	}

	@Test
	void replyIncludesOriginalCertificationAuthorAndDate() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");
		reader.addParticipant(11L, 101L, "MEMBER", "ACTIVE", "인증작성자");
		certificationReader.targets.put(55L,
				new ChatCertificationReader.ReplyTarget(55L, 101L, LocalDate.of(2026, 11, 4)));

		ChatMessageResult result = service("2026-11-09T00:00:00Z")
				.send(CHALLENGE, 10L, UUID.randomUUID(), "멋져요!", 55L);

		assertEquals(55L, result.reply().certificationId());
		assertEquals("인증작성자", result.reply().authorNickname());
		assertEquals(LocalDate.of(2026, 11, 4), result.reply().certDate());
	}

	@Test
	void replyToUnknownCertificationIsRejected() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");

		BusinessException exception = assertThrows(BusinessException.class,
				() -> service("2026-11-09T00:00:00Z").send(CHALLENGE, 10L, UUID.randomUUID(), "멋져요!", 999L));

		assertEquals(ChatErrorCode.INVALID_REPLY_TARGET, exception.getErrorCode());
		assertTrue(repository.messages.isEmpty());
	}

	@Test
	void replyStaysInListEvenIfOriginalCertificationIsGone() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");
		repository.save(ChatMessage.user(CHALLENGE, 100L, UUID.randomUUID(), "답글", 77L, LocalDateTime.of(2026, 11, 5, 0, 0)));

		ChatMessageResult result = service("2026-11-09T00:00:00Z").list(CHALLENGE, 10L, null, null, null).get(0);

		assertEquals(77L, result.reply().certificationId());
		assertNull(result.reply().authorNickname());
		assertNull(result.reply().certDate());
	}

	@Test
	void systemMessageHasNoSender() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");
		repository.save(ChatMessage.system(CHALLENGE, "경고 누적 안내", LocalDateTime.of(2026, 11, 5, 0, 0)));

		ChatMessageResult result = service("2026-11-09T00:00:00Z").list(CHALLENGE, 10L, null, null, null).get(0);

		assertEquals(ChatMessageType.SYSTEM, result.type());
		assertNull(result.sender());
		assertNull(result.clientMessageId());
		assertEquals("경고 누적 안내", result.content());
	}

	@Test
	void listReturnsAscendingWithCursors() {
		reader.addParticipant(10L, 100L, "MEMBER", "ACTIVE", "모락");
		ChatMessageService service = service("2026-11-09T00:00:00Z");
		for (int i = 1; i <= 5; i++) {
			send(service, 10L, "m" + i);
		}

		List<ChatMessageResult> latest = service.list(CHALLENGE, 10L, null, null, 3);
		assertEquals(List.of("m3", "m4", "m5"), latest.stream().map(ChatMessageResult::content).toList());
		assertNotNull(latest.get(0).sender());

		Long firstId = latest.get(0).id();
		List<ChatMessageResult> older = service.list(CHALLENGE, 10L, firstId, null, 10);
		assertEquals(List.of("m1", "m2"), older.stream().map(ChatMessageResult::content).toList());

		List<ChatMessageResult> newer = service.list(CHALLENGE, 10L, null, firstId, 10);
		assertEquals(List.of("m4", "m5"), newer.stream().map(ChatMessageResult::content).toList());

		assertEquals(ChatErrorCode.INVALID_CURSOR, assertThrows(BusinessException.class,
				() -> service.list(CHALLENGE, 10L, 3L, 1L, null)).getErrorCode());
	}

	private static class FakeReader implements ChatParticipantReader {

		final Map<Long, ChatParticipant> participants = new HashMap<>();
		final Map<Long, ChatSender> senders = new HashMap<>();

		void addParticipant(Long userId, Long participationId, String role, String status, String nickname) {
			participants.put(userId, new ChatParticipant(participationId, userId, role, status));
			senders.put(participationId, new ChatSender(userId, nickname, null, role));
		}

		@Override
		public Optional<ChatParticipant> findChatParticipant(Long challengeId, Long userId) {
			return Optional.ofNullable(participants.get(userId));
		}

		@Override
		public Map<Long, ChatSender> findSenders(Collection<Long> participationIds) {
			Map<Long, ChatSender> result = new HashMap<>();
			participationIds.forEach(id -> {
				if (senders.containsKey(id)) {
					result.put(id, senders.get(id));
				}
			});
			return result;
		}

		@Override
		public Optional<LocalDate> findChallengeEndDate(Long challengeId) {
			return Optional.of(END_DATE);
		}
	}

	private static class FakeCertificationReader implements ChatCertificationReader {

		final Map<Long, ReplyTarget> targets = new HashMap<>();

		@Override
		public Map<Long, ReplyTarget> findReplyTargets(Long challengeId, Collection<Long> certificationIds) {
			Map<Long, ReplyTarget> result = new HashMap<>();
			certificationIds.forEach(id -> {
				if (targets.containsKey(id)) {
					result.put(id, targets.get(id));
				}
			});
			return result;
		}
	}
}

package com.morak.feature.chat.presentation.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.chat.application.service.ChatSubscriptionService;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class ChatSubscribeInterceptorTest {

	private static final Long CHALLENGE = 1L;
	private static final Long MEMBER = 7L;
	private static final Long OUTSIDER = 8L;

	private final ChatSubscribeInterceptor interceptor =
			new ChatSubscribeInterceptor(new ChatSubscriptionService(new FakeReader()));

	@Test
	void participantCanSubscribeToItsChatRoom() {
		Message<byte[]> message = subscribe("/topic/challenges/1/chat", new ChatPrincipal(MEMBER));

		assertSame(message, interceptor.preSend(message, null));
	}

	@Test
	void nonParticipantCannotSubscribeToChatRoom() {
		assertRejected(subscribe("/topic/challenges/1/chat", new ChatPrincipal(OUTSIDER)), ChatErrorCode.NOT_PARTICIPANT);
	}

	@Test
	void participantCannotSubscribeToAnotherChallengeRoom() {
		assertRejected(subscribe("/topic/challenges/2/chat", new ChatPrincipal(MEMBER)), ChatErrorCode.NOT_PARTICIPANT);
	}

	@Test
	void anyLoggedInUserCanSubscribeToOwnErrorQueue() {
		Message<byte[]> message = subscribe("/user/queue/errors", new ChatPrincipal(OUTSIDER));

		assertSame(message, interceptor.preSend(message, null));
	}

	@Test
	void unknownDestinationsAreRejected() {
		for (String destination : new String[] {
			"/topic/other", "/topic/challenges/1", "/topic/challenges/1/chat/extra", "/topic/challenges/abc/chat",
			"/topic/challenges/1/chat/../2/chat", "/queue/errors", "/user/queue/other"
		}) {
			assertRejected(subscribe(destination, new ChatPrincipal(MEMBER)), ChatErrorCode.FORBIDDEN_DESTINATION);
		}
	}

	@Test
	void missingDestinationIsRejected() {
		assertRejected(subscribe(null, new ChatPrincipal(MEMBER)), ChatErrorCode.FORBIDDEN_DESTINATION);
	}

	@Test
	void unauthenticatedSubscribeIsRejected() {
		assertRejected(subscribe("/topic/challenges/1/chat", null), ChatErrorCode.UNAUTHENTICATED);
		assertRejected(subscribe("/user/queue/errors", null), ChatErrorCode.UNAUTHENTICATED);
	}

	@Test
	void foreignPrincipalTypeIsRejected() {
		Principal other = () -> "7";

		assertRejected(subscribe("/topic/challenges/1/chat", other), ChatErrorCode.UNAUTHENTICATED);
	}

	@Test
	void nonSubscribeFramesPassThrough() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/topic/anything");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		assertSame(message, interceptor.preSend(message, null));
	}

	private void assertRejected(Message<byte[]> message, ChatErrorCode expected) {
		BusinessException exception = assertThrows(BusinessException.class, () -> interceptor.preSend(message, null));
		assertEquals(expected, exception.getErrorCode());
	}

	private Message<byte[]> subscribe(String destination, Principal user) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setSessionId("session-1");
		accessor.setSubscriptionId("sub-0");
		accessor.setDestination(destination);
		accessor.setUser(user);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	/** 챌린지 1의 참가자는 유저 7뿐이다. */
	private static class FakeReader implements ChatParticipantReader {

		@Override
		public Optional<ChatParticipant> findChatParticipant(Long challengeId, Long userId) {
			if (CHALLENGE.equals(challengeId) && MEMBER.equals(userId)) {
				return Optional.of(new ChatParticipant(100L, userId, "MEMBER", "ACTIVE"));
			}
			return Optional.empty();
		}

		@Override
		public Map<Long, ChatSender> findSenders(Collection<Long> participationIds) {
			return Map.of();
		}

		@Override
		public Optional<LocalDate> findChallengeEndDate(Long challengeId) {
			return Optional.empty();
		}
	}
}

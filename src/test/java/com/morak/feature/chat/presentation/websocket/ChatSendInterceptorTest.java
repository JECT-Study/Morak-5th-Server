package com.morak.feature.chat.presentation.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class ChatSendInterceptorTest {

	private final ChatSendInterceptor interceptor = new ChatSendInterceptor();

	@Test
	void sendToChatRoomPathPasses() {
		Message<byte[]> message = send("/app/challenges/1/chat", new ChatPrincipal(7L));

		assertSame(message, interceptor.preSend(message, null));
	}

	@Test
	void sendDirectlyToBrokerDestinationsIsRejected() {
		for (String destination : new String[] {
			"/topic/challenges/1/chat", "/queue/errors", "/user/queue/errors", "/app/other", "/app/challenges/1",
			"/app/challenges/abc/chat", "/app/challenges/1/chat/extra", "/app/challenges/1/chat/../2/chat", "/ws"
		}) {
			assertRejected(send(destination, new ChatPrincipal(7L)), ChatErrorCode.FORBIDDEN_DESTINATION);
		}
	}

	@Test
	void missingDestinationIsRejected() {
		assertRejected(send(null, new ChatPrincipal(7L)), ChatErrorCode.FORBIDDEN_DESTINATION);
	}

	@Test
	void unauthenticatedOrForeignPrincipalIsRejected() {
		Principal foreign = () -> "7";

		assertRejected(send("/app/challenges/1/chat", null), ChatErrorCode.UNAUTHENTICATED);
		assertRejected(send("/app/challenges/1/chat", foreign), ChatErrorCode.UNAUTHENTICATED);
	}

	@Test
	void nonSendFramesPassThrough() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/challenges/1/chat");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		assertSame(message, interceptor.preSend(message, null));
	}

	private void assertRejected(Message<byte[]> message, ChatErrorCode expected) {
		BusinessException exception = assertThrows(BusinessException.class, () -> interceptor.preSend(message, null));
		assertEquals(expected, exception.getErrorCode());
	}

	private Message<byte[]> send(String destination, Principal user) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setSessionId("session-1");
		accessor.setDestination(destination);
		accessor.setUser(user);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}

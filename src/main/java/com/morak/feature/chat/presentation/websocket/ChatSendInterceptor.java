package com.morak.feature.chat.presentation.websocket;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * STOMP SEND 때 보낼 수 있는 경로를 제한한다.
 * 허용 경로는 /app/challenges/{challengeId}/chat 뿐이다. 서버 메모리 브로커는 클라이언트가 /topic 으로 직접 SEND 해도
 * 구독자에게 그대로 전달하므로, 막지 않으면 참가자가 전송 검증(참가 상태·중복 방지)을 건너뛰고 메시지를 위조할 수 있다.
 */
public class ChatSendInterceptor implements ChannelInterceptor {

	private static final Pattern SEND_DESTINATION = Pattern.compile("^/app/challenges/\\d{1,18}/chat$");

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() != StompCommand.SEND) {
			return message;
		}
		if (!(accessor.getUser() instanceof ChatPrincipal)) {
			throw new BusinessException(ChatErrorCode.UNAUTHENTICATED);
		}
		String destination = accessor.getDestination();
		if (destination == null || !SEND_DESTINATION.matcher(destination).matches()) {
			throw new BusinessException(ChatErrorCode.FORBIDDEN_DESTINATION);
		}
		return message;
	}
}

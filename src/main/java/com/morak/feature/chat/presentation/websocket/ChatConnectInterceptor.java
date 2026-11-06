package com.morak.feature.chat.presentation.websocket;

import com.morak.feature.chat.application.service.ChatTicketService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * STOMP CONNECT 때 ticket 헤더로 사용자를 확인한다.
 * 티켓이 올바르면 세션 사용자(ChatPrincipal)를 설정하고, 이후 SEND·SUBSCRIBE 프레임에도 이 사용자가 유지된다.
 */
public class ChatConnectInterceptor implements ChannelInterceptor {

	static final String TICKET_HEADER = "ticket";

	private final ChatTicketService ticketService;

	public ChatConnectInterceptor(ChatTicketService ticketService) {
		this.ticketService = ticketService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
			return message;
		}
		Long userId = ticketService.consume(accessor.getFirstNativeHeader(TICKET_HEADER));
		accessor.setUser(new ChatPrincipal(userId));
		return message;
	}
}

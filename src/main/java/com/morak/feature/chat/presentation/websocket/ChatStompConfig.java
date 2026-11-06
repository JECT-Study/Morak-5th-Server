package com.morak.feature.chat.presentation.websocket;

import com.morak.feature.chat.application.service.ChatSubscriptionService;
import com.morak.feature.chat.application.service.ChatTicketService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** 클라이언트가 보낸 STOMP 프레임에 채팅 인증·권한 검사를 붙인다. 등록 순서대로 실행된다. */
@Configuration
public class ChatStompConfig implements WebSocketMessageBrokerConfigurer {

	private final ChatTicketService ticketService;
	private final ChatSubscriptionService subscriptionService;

	public ChatStompConfig(ChatTicketService ticketService, ChatSubscriptionService subscriptionService) {
		this.ticketService = ticketService;
		this.subscriptionService = subscriptionService;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.setErrorHandler(new ChatStompErrorHandler());
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(
				new ChatConnectInterceptor(ticketService),
				new ChatSubscribeInterceptor(subscriptionService),
				new ChatSendInterceptor());
	}
}

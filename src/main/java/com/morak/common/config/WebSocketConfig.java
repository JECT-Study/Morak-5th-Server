package com.morak.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 채팅 STOMP 설정.
 * - 연결: /ws
 * - 보내기(SEND): /app/** → @MessageMapping
 * - 받기(SUBSCRIBE): /topic/** (방 전체), /user/queue/** (나에게만)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	/** 서버·클라이언트가 10초마다 heartbeat를 주고받아 끊긴 연결을 감지한다. */
	private static final long[] HEARTBEAT_MILLIS = {10_000, 10_000};

	private final String[] allowedOrigins;
	private TaskScheduler messageBrokerTaskScheduler;

	public WebSocketConfig(@Value("${morak.chat.ws.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Autowired
	void setMessageBrokerTaskScheduler(@Lazy TaskScheduler messageBrokerTaskScheduler) {
		this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// 브라우저가 BFF를 거치지 않고 직접 연결하므로 프론트 origin만 허용한다.
		registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// 서버 메모리 브로커. 서버를 여러 대로 늘리면 Redis Pub/Sub 등 외부 브로커로 교체해야 한다.
		registry.enableSimpleBroker("/topic", "/queue")
				.setHeartbeatValue(HEARTBEAT_MILLIS)
				.setTaskScheduler(messageBrokerTaskScheduler);
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}
}

package com.morak.feature.chat.presentation.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.morak.common.config.WebSocketConfig;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.chat.application.service.ChatSubscriptionService;
import com.morak.feature.chat.application.service.ChatTicketService;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import com.morak.feature.chat.infrastructure.ticket.InMemoryChatTicketStore;
import java.lang.reflect.Type;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** 실제 웹소켓으로 접속해, CONNECT 티켓 인증과 SUBSCRIBE 권한 검사가 이어서 동작하는지 확인한다. */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = ChatStompSecurityTest.TestApplication.class,
		properties = "morak.chat.ws.allowed-origins=http://localhost:3000")
class ChatStompSecurityTest {

	private static final Long CHALLENGE = 1L;
	private static final Long MEMBER = 7L;
	private static final Long OUTSIDER = 8L;
	private static final String SUBSCRIBED = "SUBSCRIBED";

	@LocalServerPort
	private int port;

	@Autowired
	private ChatTicketService ticketService;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Test
	void connectWithoutTicketIsRejected() throws Exception {
		assertEquals(ChatErrorCode.INVALID_TICKET.code(), connect(null).error.get(5, TimeUnit.SECONDS));
	}

	@Test
	void connectWithUnknownTicketIsRejected() throws Exception {
		assertEquals(ChatErrorCode.INVALID_TICKET.code(), connect("not-issued-ticket").error.get(5, TimeUnit.SECONDS));
	}

	@Test
	void ticketCannotBeReused() throws Exception {
		String ticket = ticketService.issue(MEMBER).ticket();
		connect(ticket).session.get(5, TimeUnit.SECONDS).disconnect();

		assertEquals(ChatErrorCode.INVALID_TICKET.code(), connect(ticket).error.get(5, TimeUnit.SECONDS));
	}

	@Test
	void participantCanSubscribeToItsChatRoom() throws Exception {
		assertEquals(SUBSCRIBED, subscribeAs(MEMBER, "/topic/challenges/1/chat"));
	}

	@Test
	void anyLoggedInUserCanSubscribeToOwnErrorQueue() throws Exception {
		assertEquals(SUBSCRIBED, subscribeAs(OUTSIDER, "/user/queue/errors"));
	}

	@Test
	void nonParticipantSubscribeIsRejected() throws Exception {
		assertEquals(ChatErrorCode.NOT_PARTICIPANT.code(), subscribeAs(OUTSIDER, "/topic/challenges/1/chat"));
	}

	@Test
	void unknownDestinationSubscribeIsRejected() throws Exception {
		assertEquals(ChatErrorCode.FORBIDDEN_DESTINATION.code(), subscribeAs(MEMBER, "/topic/other"));
	}

	/**
	 * 티켓으로 접속해 구독한다. 서버가 해당 경로로 발행한 메시지를 받으면 SUBSCRIBED,
	 * 서버가 ERROR 프레임으로 거부하면 그 에러 코드를 돌려준다.
	 */
	private String subscribeAs(Long userId, String destination) throws Exception {
		TestClient client = connect(ticketService.issue(userId).ticket());
		StompSession session = client.session.get(5, TimeUnit.SECONDS);
		CompletableFuture<String> received = new CompletableFuture<>();
		session.subscribe(destination, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return byte[].class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.complete(SUBSCRIBED);
			}
		});

		CompletableFuture<Object> outcome = CompletableFuture.anyOf(received, client.error);
		for (int attempt = 0; attempt < 50 && !outcome.isDone(); attempt++) {
			publish(userId, destination);
			Thread.sleep(100);
		}
		String result = (String) outcome.get(1, TimeUnit.SECONDS);
		try {
			session.disconnect();
		} catch (RuntimeException closedAlready) {
			// 서버가 ERROR 프레임 뒤에 연결을 닫은 경우
		}
		return result;
	}

	/** 구독이 서버에 등록되는 시점을 알 수 없으므로, 받을 때까지 반복 발행한다. */
	private void publish(Long userId, String destination) {
		if (destination.startsWith("/user/")) {
			messagingTemplate.convertAndSendToUser(String.valueOf(userId), destination.substring("/user".length()), "hi");
		} else {
			messagingTemplate.convertAndSend(destination, "hi");
		}
	}

	private TestClient connect(String ticket) {
		TestClient client = new TestClient();
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.initialize();
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setTaskScheduler(scheduler);

		WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
		handshakeHeaders.setOrigin("http://localhost:3000");
		StompHeaders connectHeaders = new StompHeaders();
		if (ticket != null) {
			connectHeaders.add("ticket", ticket);
		}
		stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, connectHeaders,
				new StompSessionHandlerAdapter() {
					@Override
					public void afterConnected(StompSession session, StompHeaders headers) {
						client.session.complete(session);
					}

					@Override
					public void handleFrame(StompHeaders headers, Object payload) {
						client.error.complete(headers.getFirst("code"));
					}
				});
		return client;
	}

	private static class TestClient {

		final CompletableFuture<StompSession> session = new CompletableFuture<>();
		final CompletableFuture<String> error = new CompletableFuture<>();
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration({
		TomcatServletWebServerAutoConfiguration.class,
		DispatcherServletAutoConfiguration.class,
		WebMvcAutoConfiguration.class,
		JacksonAutoConfiguration.class
	})
	@Import({
		WebSocketConfig.class,
		ChatStompConfig.class,
		ChatTicketService.class,
		ChatSubscriptionService.class,
		InMemoryChatTicketStore.class
	})
	static class TestApplication {

		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}

		/** 챌린지 1의 참가자는 유저 7뿐이다. */
		@Bean
		ChatParticipantReader participantReader() {
			return new ChatParticipantReader() {
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
			};
		}
	}
}

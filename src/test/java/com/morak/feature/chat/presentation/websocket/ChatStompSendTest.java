package com.morak.feature.chat.presentation.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.config.WebSocketConfig;
import com.morak.feature.chat.application.port.out.ChatCertificationReader;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.chat.application.service.ChatMessageService;
import com.morak.feature.chat.application.service.ChatSubscriptionService;
import com.morak.feature.chat.application.service.ChatTicketService;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import com.morak.feature.chat.infrastructure.ticket.InMemoryChatTicketStore;
import com.morak.feature.chat.support.InMemoryChatMessageRepository;
import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** 실제 웹소켓으로 접속해 메시지 전송 → 방 전체 발행 / 실패는 보낸 사람에게만 알림이 가는지 확인한다. */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = ChatStompSendTest.TestApplication.class,
		properties = "morak.chat.ws.allowed-origins=http://localhost:3000")
class ChatStompSendTest {

	private static final Long CHALLENGE = 1L;
	private static final Long HOST = 7L;
	private static final Long FRIEND = 9L;
	private static final Long OUTSIDER = 8L;
	private static final String TOPIC = "/topic/challenges/1/chat";
	private static final String SEND = "/app/challenges/1/chat";
	private static final String ERRORS = "/user/queue/errors";
	private static final long WAIT_SECONDS = 5;
	private static final String PROBE = "__ready__";

	@LocalServerPort
	private int port;

	@Autowired
	private ChatTicketService ticketService;

	@Autowired
	private InMemoryChatMessageRepository repository;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@BeforeEach
	void clearMessages() {
		repository.messages.clear();
	}

	@Test
	void sentMessageIsBroadcastToEveryParticipantWithSenderAndUtcTime() throws Exception {
		Connection host = connect(HOST);
		Connection friend = connect(FRIEND);
		BlockingQueue<String> hostTopic = host.subscribe(TOPIC);
		BlockingQueue<String> friendTopic = friend.subscribe(TOPIC);
		String clientMessageId = UUID.randomUUID().toString();

		host.send(SEND, json(clientMessageId, "안녕하세요!"));

		for (BlockingQueue<String> topic : List.of(hostTopic, friendTopic)) {
			String frame = receive(topic, WAIT_SECONDS * 1000);
			assertNotNull(frame, "방 참가자 모두 메시지를 받아야 한다");
			assertTrue(frame.contains("\"clientMessageId\":\"" + clientMessageId + "\""), frame);
			assertTrue(frame.contains("\"content\":\"안녕하세요!\""), frame);
			assertTrue(frame.contains("\"type\":\"USER\""), frame);
			assertTrue(frame.contains("\"nickname\":\"모락\""), frame);
			assertTrue(frame.contains("\"role\":\"HOST\""), frame);
			assertTrue(frame.contains("\"createdAt\":\"2026-11-05T06:00:00Z\""), frame);
		}
		assertEquals(1, repository.messages.size());
	}

	@Test
	void resendWithSameClientMessageIdIsStoredOnceAndDeliveredAgain() throws Exception {
		Connection host = connect(HOST);
		BlockingQueue<String> topic = host.subscribe(TOPIC);
		String clientMessageId = UUID.randomUUID().toString();

		host.send(SEND, json(clientMessageId, "재전송 테스트"));
		String first = receive(topic, WAIT_SECONDS * 1000);
		host.send(SEND, json(clientMessageId, "재전송 테스트"));
		String second = receive(topic, WAIT_SECONDS * 1000);

		assertNotNull(first);
		assertEquals(first, second, "같은 메시지(같은 id)가 다시 내려와야 한다");
		assertEquals(1, repository.messages.size());
	}

	@Test
	void invalidClientMessageIdIsReportedOnlyToSender() throws Exception {
		Connection host = connect(HOST);
		Connection friend = connect(FRIEND);
		BlockingQueue<String> hostErrors = host.subscribe(ERRORS);
		BlockingQueue<String> friendTopic = friend.subscribe(TOPIC);

		host.send(SEND, json("not-a-uuid", "안녕"));

		String error = receive(hostErrors, WAIT_SECONDS * 1000);
		assertNotNull(error, "보낸 사람에게 실패 알림이 와야 한다");
		assertTrue(error.contains("\"code\":\"" + ChatErrorCode.INVALID_CLIENT_MESSAGE_ID.code() + "\""), error);
		assertTrue(error.contains("\"clientMessageId\":\"not-a-uuid\""), error);
		assertNull(receive(friendTopic, 500), "다른 참가자에게는 아무것도 가지 않는다");
		assertTrue(repository.messages.isEmpty());
	}

	@Test
	void nonParticipantSendIsReportedToSenderAndNothingIsStored() throws Exception {
		Connection outsider = connect(OUTSIDER);
		Connection friend = connect(FRIEND);
		BlockingQueue<String> outsiderErrors = outsider.subscribe(ERRORS);
		BlockingQueue<String> friendTopic = friend.subscribe(TOPIC);
		String clientMessageId = UUID.randomUUID().toString();

		outsider.send(SEND, json(clientMessageId, "들어가도 되나요"));

		String error = receive(outsiderErrors, WAIT_SECONDS * 1000);
		assertNotNull(error);
		assertTrue(error.contains("\"code\":\"" + ChatErrorCode.NOT_PARTICIPANT.code() + "\""), error);
		assertTrue(error.contains("\"clientMessageId\":\"" + clientMessageId + "\""), error);
		assertNull(receive(friendTopic, 500));
		assertTrue(repository.messages.isEmpty());
	}

	@Test
	void malformedBodyIsReportedToSenderWithoutClientMessageId() throws Exception {
		Connection host = connect(HOST);
		BlockingQueue<String> hostErrors = host.subscribe(ERRORS);

		host.send(SEND, "이건 JSON이 아니다");

		String error = receive(hostErrors, WAIT_SECONDS * 1000);
		assertNotNull(error);
		assertTrue(error.contains("\"code\":\"COMMON_001\""), error);
		assertTrue(error.contains("\"clientMessageId\":null"), error);
	}

	@Test
	void sendingDirectlyToTopicIsRejectedAndNotDelivered() throws Exception {
		Connection host = connect(HOST);
		Connection friend = connect(FRIEND);
		BlockingQueue<String> friendTopic = friend.subscribe(TOPIC);

		host.send(TOPIC, "{\"content\":\"위조된 메시지\"}");

		assertEquals(ChatErrorCode.FORBIDDEN_DESTINATION.code(), host.errorCode.get(WAIT_SECONDS, TimeUnit.SECONDS));
		assertNull(receive(friendTopic, 500), "브로커로 직접 보낸 메시지는 전달되면 안 된다");
		assertTrue(repository.messages.isEmpty());
	}

	/** 준비 신호(probe)는 건너뛰고 다음 프레임을 돌려준다. 시간 안에 오지 않으면 null. */
	private String receive(BlockingQueue<String> frames, long timeoutMillis) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (true) {
			long remaining = deadline - System.currentTimeMillis();
			String frame = remaining <= 0 ? frames.poll() : frames.poll(remaining, TimeUnit.MILLISECONDS);
			if (frame == null || !PROBE.equals(frame)) {
				return frame;
			}
		}
	}

	private String json(String clientMessageId, String content) {
		return "{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"" + content + "\"}";
	}

	private Connection connect(Long userId) throws Exception {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.initialize();
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setTaskScheduler(scheduler);
		stompClient.setMessageConverter(new JsonAsStringConverter());

		Connection connection = new Connection(userId);
		WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
		handshakeHeaders.setOrigin("http://localhost:3000");
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add("ticket", ticketService.issue(userId).ticket());
		stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, connectHeaders,
				new StompSessionHandlerAdapter() {
					@Override
					public void afterConnected(StompSession session, StompHeaders headers) {
						connection.session.complete(session);
					}

					@Override
					public void handleFrame(StompHeaders headers, Object payload) {
						connection.errorCode.complete(headers.getFirst("code"));
					}
				});
		connection.session.get(WAIT_SECONDS, TimeUnit.SECONDS);
		return connection;
	}

	/** JSON 본문을 파싱하지 않고 문자열 그대로 보내고 받는다. */
	private static class JsonAsStringConverter extends StringMessageConverter {

		JsonAsStringConverter() {
			addSupportedMimeTypes(MimeTypeUtils.APPLICATION_JSON);
		}
	}

	private class Connection {

		final Long userId;
		final CompletableFuture<StompSession> session = new CompletableFuture<>();
		final CompletableFuture<String> errorCode = new CompletableFuture<>();

		Connection(Long userId) {
			this.userId = userId;
		}

		/**
		 * 구독이 서버에 등록되는 시점을 알 수 없으므로, 준비 신호를 받을 때까지 반복 발행한다.
		 * 신호를 받으면 구독이 등록된 것이므로 큐에서 신호를 지우고, 이후 받은 프레임 본문이 쌓이는 큐를 돌려준다.
		 */
		BlockingQueue<String> subscribe(String destination) throws Exception {
			BlockingQueue<String> frames = new LinkedBlockingQueue<>();
			session.get().subscribe(destination, new StompFrameHandler() {
				@Override
				public Type getPayloadType(StompHeaders headers) {
					return String.class;
				}

				@Override
				public void handleFrame(StompHeaders headers, Object payload) {
					frames.add((String) payload);
				}
			});
			for (int attempt = 0; attempt < 50 && !frames.contains(PROBE); attempt++) {
				publishProbe(destination);
				Thread.sleep(100);
			}
			assertTrue(frames.contains(PROBE), destination + " 구독이 등록되지 않았다");
			Thread.sleep(200);
			frames.removeIf(PROBE::equals);
			return frames;
		}

		void send(String destination, String body) throws Exception {
			StompHeaders headers = new StompHeaders();
			headers.setDestination(destination);
			headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
			session.get().send(headers, body);
		}

		private void publishProbe(String destination) {
			if (destination.startsWith("/user/")) {
				messagingTemplate.convertAndSendToUser(
						String.valueOf(userId), destination.substring("/user".length()), PROBE);
			} else {
				messagingTemplate.convertAndSend(destination, PROBE);
			}
		}
	}

	@SpringBootConfiguration
	@ImportAutoConfiguration({
		TomcatServletWebServerAutoConfiguration.class,
		DispatcherServletAutoConfiguration.class,
		WebMvcAutoConfiguration.class,
		JacksonAutoConfiguration.class
	})
	@Import({
		WebSocketConfig.class,
		ChatStompConfig.class,
		ChatStompController.class,
		ChatMessageService.class,
		ChatTicketService.class,
		ChatSubscriptionService.class,
		InMemoryChatTicketStore.class
	})
	static class TestApplication {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-11-05T06:00:00Z"), ZoneOffset.UTC);
		}

		@Bean
		InMemoryChatMessageRepository chatMessageRepository() {
			return new InMemoryChatMessageRepository();
		}

		@Bean
		ChatCertificationReader certificationReader() {
			return (challengeId, certificationIds) -> Map.of();
		}

		/** 챌린지 1의 참가자는 유저 7(방장)과 9뿐이다. */
		@Bean
		ChatParticipantReader participantReader() {
			Map<Long, ChatParticipantReader.ChatParticipant> participants = Map.of(
					HOST, new ChatParticipantReader.ChatParticipant(100L, HOST, "HOST", "ACTIVE"),
					FRIEND, new ChatParticipantReader.ChatParticipant(101L, FRIEND, "MEMBER", "ACTIVE"));
			Map<Long, ChatParticipantReader.ChatSender> senders = Map.of(
					100L, new ChatParticipantReader.ChatSender(HOST, "모락", null, "HOST"),
					101L, new ChatParticipantReader.ChatSender(FRIEND, "친구", null, "MEMBER"));
			return new ChatParticipantReader() {
				@Override
				public Optional<ChatParticipant> findChatParticipant(Long challengeId, Long userId) {
					if (!CHALLENGE.equals(challengeId)) {
						return Optional.empty();
					}
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
					return Optional.of(LocalDate.of(2026, 11, 10));
				}
			};
		}
	}
}

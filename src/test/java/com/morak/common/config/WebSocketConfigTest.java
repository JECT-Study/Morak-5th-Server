package com.morak.common.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** DB 없이 웹 서버와 STOMP 설정만 띄워 /ws 연결을 확인한다. */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = WebSocketConfigTest.TestApplication.class,
		properties = "morak.chat.ws.allowed-origins=http://localhost:3000")
class WebSocketConfigTest {

	@LocalServerPort
	private int port;

	@Test
	void allowedOriginCanConnectWithHeartbeat() throws Exception {
		CompletableFuture<StompHeaders> connected = new CompletableFuture<>();
		StompSession session = connect("http://localhost:3000", connected);

		assertTrue(session.isConnected());
		assertArrayEquals(new long[] {10_000, 10_000}, connected.get(5, TimeUnit.SECONDS).getHeartbeat());
		session.disconnect();
	}

	@Test
	void unknownOriginIsRejected() {
		assertThrows(ExecutionException.class, () -> connect("http://evil.example.com", new CompletableFuture<>()));
	}

	private StompSession connect(String origin, CompletableFuture<StompHeaders> connected) throws Exception {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.initialize();
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setTaskScheduler(scheduler);
		client.setDefaultHeartbeat(new long[] {10_000, 10_000});

		WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
		handshakeHeaders.setOrigin(origin);
		return client.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, new StompHeaders(),
						new StompSessionHandlerAdapter() {
							@Override
							public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
								connected.complete(connectedHeaders);
							}
						})
				.get(5, TimeUnit.SECONDS);
	}

	@SpringBootConfiguration
	@ImportAutoConfiguration({
		TomcatServletWebServerAutoConfiguration.class,
		DispatcherServletAutoConfiguration.class,
		WebMvcAutoConfiguration.class,
		JacksonAutoConfiguration.class
	})
	@Import(WebSocketConfig.class)
	static class TestApplication {
	}
}

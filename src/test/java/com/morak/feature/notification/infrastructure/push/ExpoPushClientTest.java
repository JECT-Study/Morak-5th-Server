package com.morak.feature.notification.infrastructure.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.notification.application.dto.PushMessage;
import com.morak.feature.notification.application.dto.PushReceipt;
import com.morak.feature.notification.application.dto.PushTicket;
import com.morak.feature.notification.application.port.out.PushGateway;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 실제 HTTP 서버(가짜 Expo)를 띄워 요청 형식과 응답 해석을 확인한다. */
class ExpoPushClientTest {

	private final ObjectMapper mapper = new ObjectMapper();
	private final LinkedBlockingQueue<Captured> requests = new LinkedBlockingQueue<>();
	private HttpServer server;
	private String sendResponse = "{\"data\":[]}";
	private String receiptsResponse = "{\"data\":{}}";
	private int status = 200;

	record Captured(String method, String path, String authorization, String contentType, String body) {}

	private ExpoPushClient client(String accessToken) throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			try (exchange) {
				requests.add(new Captured(
						exchange.getRequestMethod(),
						exchange.getRequestURI().getPath(),
						exchange.getRequestHeaders().getFirst("Authorization"),
						exchange.getRequestHeaders().getFirst("Content-Type"),
						new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
				byte[] response = (exchange.getRequestURI().getPath().endsWith("send") ? sendResponse : receiptsResponse)
						.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(status, response.length);
				exchange.getResponseBody().write(response);
			}
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		return new ExpoPushClient(base + "/push/send", base + "/push/getReceipts", accessToken);
	}

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sendsExpoFormatWithDataUrlOnlyWhenThereIsOne() throws Exception {
		ExpoPushClient client = client("");
		sendResponse = "{\"data\":[{\"status\":\"ok\",\"id\":\"t1\"},{\"status\":\"ok\",\"id\":\"t2\"}]}";

		client.send(List.of(
				new PushMessage("ExponentPushToken[a]", "참여 확정", "본문", "/challenges/5"),
				new PushMessage("ExponentPushToken[b]", "제목", "본문2", null)));

		Captured request = requests.take();
		assertEquals("POST", request.method());
		assertEquals("/push/send", request.path());
		assertTrue(request.contentType().startsWith("application/json"));
		JsonNode body = mapper.readTree(request.body());
		assertEquals(2, body.size());
		assertEquals("ExponentPushToken[a]", body.get(0).path("to").asString());
		assertEquals("참여 확정", body.get(0).path("title").asString());
		assertEquals("본문", body.get(0).path("body").asString());
		assertEquals("default", body.get(0).path("sound").asString());
		assertEquals("/challenges/5", body.get(0).path("data").path("url").asString());
		assertFalse(body.get(1).has("data"));
	}

	@Test
	void parsesAcceptedAndRejectedTickets() throws Exception {
		ExpoPushClient client = client("");
		sendResponse = """
				{"data":[
				{"status":"ok","id":"ticket-1"},
				{"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}},
				{"status":"error","message":"something"}
				],"extra":"ignored"}
				""";

		List<PushTicket> tickets = client.send(List.of(
				new PushMessage("ExponentPushToken[a]", "t", "b", null),
				new PushMessage("ExponentPushToken[b]", "t", "b", null),
				new PushMessage("ExponentPushToken[c]", "t", "b", null)));

		assertEquals(PushTicket.accepted("ticket-1"), tickets.get(0));
		assertEquals(PushTicket.rejected(PushGateway.DEVICE_NOT_REGISTERED), tickets.get(1));
		assertEquals(PushTicket.rejected("Unknown"), tickets.get(2));
		assertTrue(tickets.get(0).isAccepted());
		assertFalse(tickets.get(1).isAccepted());
	}

	@Test
	void addsBearerHeaderOnlyWhenAccessTokenIsConfigured() throws Exception {
		ExpoPushClient withoutToken = client("");
		withoutToken.send(List.of());
		assertNull(requests.take().authorization());
		stop();

		ExpoPushClient withToken = client("secret-access-token");
		withToken.send(List.of());
		assertEquals("Bearer secret-access-token", requests.take().authorization());
	}

	@Test
	void requestsReceiptsByTicketIdsAndParsesResult() throws Exception {
		ExpoPushClient client = client("");
		receiptsResponse = """
				{"data":{
				"t1":{"status":"ok"},
				"t2":{"status":"error","message":"gone","details":{"error":"DeviceNotRegistered"}},
				"t3":{"status":"error","message":"x"}
				}}
				""";

		Map<String, PushReceipt> receipts = client.getReceipts(List.of("t1", "t2", "t3"));

		Captured request = requests.take();
		assertEquals("/push/getReceipts", request.path());
		JsonNode body = mapper.readTree(request.body());
		assertEquals(3, body.path("ids").size());
		assertEquals("t2", body.path("ids").get(1).asString());
		assertTrue(receipts.get("t1").isOk());
		assertEquals(PushReceipt.failed(PushGateway.DEVICE_NOT_REGISTERED), receipts.get("t2"));
		assertEquals(PushReceipt.failed("Unknown"), receipts.get("t3"));
	}

	@Test
	void serverErrorIsThrownToCaller() throws Exception {
		ExpoPushClient client = client("");
		status = 500;
		sendResponse = "{\"errors\":[{\"code\":\"INTERNAL\"}]}";

		assertThrows(
				RuntimeException.class, () -> client.send(List.of(new PushMessage("ExponentPushToken[a]", "t", "b", null))));
	}

	@Test
	void responseWithoutDataIsRejected() throws Exception {
		ExpoPushClient client = client("");
		sendResponse = "{}";
		receiptsResponse = "{}";

		assertThrows(IllegalStateException.class, () -> client.send(List.of()));
		assertThrows(IllegalStateException.class, () -> client.getReceipts(List.of("t1")));
	}
}

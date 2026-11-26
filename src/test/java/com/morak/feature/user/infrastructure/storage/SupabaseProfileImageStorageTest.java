package com.morak.feature.user.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;

class SupabaseProfileImageStorageTest {

	@Test
	void uploadsReturnsPublicUrlAndRecognizesOnlyItsOwnUrls() throws Exception {
		var requests = new LinkedBlockingQueue<String>();
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/storage/v1/", exchange -> {
			try (exchange) {
				requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
				exchange.getRequestBody().readAllBytes();
				exchange.sendResponseHeaders(200, -1);
			}
		});
		server.start();

		try {
			String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			var storage = new SupabaseProfileImageStorage(baseUrl + "/", "sb_secret_test", "profiles");

			String url = storage.upload("users/7/abc", new byte[] {1}, "image/png");
			storage.delete("users/7/abc");

			assertEquals(baseUrl + "/storage/v1/object/public/profiles/users/7/abc", url);
			assertEquals("POST /storage/v1/object/profiles/users/7/abc", requests.remove());
			assertEquals("DELETE /storage/v1/object/profiles", requests.remove());
			assertEquals(Optional.of("users/7/abc"), storage.keyOf(url));
			assertEquals(Optional.empty(), storage.keyOf("https://k.kakaocdn.net/me.png"));
		} finally {
			server.stop(0);
		}
	}
}

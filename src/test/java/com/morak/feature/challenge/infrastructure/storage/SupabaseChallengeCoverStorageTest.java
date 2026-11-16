package com.morak.feature.challenge.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SupabaseChallengeCoverStorageTest {

	@Test
	void uploadsAndDeletesThroughStorageHttpApi() throws Exception {
		String key = "challenges/test-cover";
		byte[] image = {1, 2, 3};
		var requests = new LinkedBlockingQueue<StorageRequest>();
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/storage/v1/", exchange -> {
			try (exchange) {
				var headers = exchange.getRequestHeaders();
				String path = exchange.getRequestURI().getPath();
				requests.add(new StorageRequest(exchange.getRequestMethod(), path, headers.getFirst("apikey"),
						headers.getFirst("Content-Type"), headers.getFirst("x-upsert"), exchange.getRequestBody().readAllBytes()));
				exchange.sendResponseHeaders(200, -1);
			}
		});
		server.start();

		try {
			String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			var storage = new SupabaseChallengeCoverStorage(baseUrl, "sb_secret_test", "covers");
			storage.upload(key, image, "image/png");
			storage.delete(key);

			var upload = requests.remove();
			assertEquals("POST", upload.method());
			assertEquals("/storage/v1/object/covers/" + key, upload.path());
			assertEquals("sb_secret_test", upload.apiKey());
			assertEquals("image/png", upload.contentType());
			assertEquals("false", upload.upsert());
			assertArrayEquals(image, upload.body());

			var mapper = new ObjectMapper();
			var delete = requests.remove();
			assertEquals("DELETE", delete.method());
			assertEquals("/storage/v1/object/covers", delete.path());
			assertEquals(key, mapper.readTree(delete.body()).path("prefixes").get(0).asString());
		} finally {
			server.stop(0);
		}
	}

	private record StorageRequest(String method, String path, String apiKey, String contentType, String upsert, byte[] body) {}
}

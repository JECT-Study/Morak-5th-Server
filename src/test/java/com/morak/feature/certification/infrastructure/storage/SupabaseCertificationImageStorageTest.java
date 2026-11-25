package com.morak.feature.certification.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SupabaseCertificationImageStorageTest {

	@Test
	void uploadsSignsAndDeletesThroughStorageHttpApi() throws Exception {
		String key = "certifications/7/test-image";
		byte[] image = {1, 2, 3};
		var requests = new LinkedBlockingQueue<StorageRequest>();
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/storage/v1/", exchange -> {
			try (exchange) {
				var headers = exchange.getRequestHeaders();
				String path = exchange.getRequestURI().getPath();
				requests.add(new StorageRequest(exchange.getRequestMethod(), path, headers.getFirst("apikey"),
						headers.getFirst("Content-Type"), headers.getFirst("x-upsert"), exchange.getRequestBody().readAllBytes()));
				if (path.equals("/storage/v1/object/sign/certifications")) {
					byte[] result = """
							[{"path":"certifications/7/test-image","signedURL":"/object/sign/certifications/certifications/7/test-image?token=test"}]
							""".getBytes(StandardCharsets.UTF_8);
					exchange.getResponseHeaders().set("Content-Type", "application/json");
					exchange.sendResponseHeaders(200, result.length);
					exchange.getResponseBody().write(result);
				} else {
					exchange.sendResponseHeaders(200, -1);
				}
			}
		});
		server.start();

		try {
			String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			var storage = new SupabaseCertificationImageStorage(baseUrl, "sb_secret_test", "certifications");
			storage.upload(key, image, "image/png");
			assertEquals(Map.of(key, baseUrl + "/storage/v1/object/sign/certifications/" + key + "?token=test"),
					storage.signDownloads(List.of(key)));
			storage.delete(key);

			var upload = requests.remove();
			assertEquals("POST", upload.method());
			assertEquals("/storage/v1/object/certifications/" + key, upload.path());
			assertEquals("sb_secret_test", upload.apiKey());
			assertEquals("image/png", upload.contentType());
			assertEquals("false", upload.upsert());
			assertArrayEquals(image, upload.body());

			var mapper = new ObjectMapper();
			var sign = requests.remove();
			assertEquals("POST", sign.method());
			assertEquals("/storage/v1/object/sign/certifications", sign.path());
			assertEquals(key, mapper.readTree(sign.body()).path("paths").get(0).asString());
			assertEquals(300, mapper.readTree(sign.body()).path("expiresIn").asInt());

			var delete = requests.remove();
			assertEquals("DELETE", delete.method());
			assertEquals("/storage/v1/object/certifications", delete.path());
			assertEquals(key, mapper.readTree(delete.body()).path("prefixes").get(0).asString());
		} finally {
			server.stop(0);
		}
	}

	private record StorageRequest(String method, String path, String apiKey, String contentType, String upsert, byte[] body) {}
}

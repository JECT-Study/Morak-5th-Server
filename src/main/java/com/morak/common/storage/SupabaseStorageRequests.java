package com.morak.common.storage;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class SupabaseStorageRequests {

	private SupabaseStorageRequests() {}

	public static RestClient createClient(String storageUrl, String apiKey) {
		var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3)).build());
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		var builder = RestClient.builder()
				.baseUrl(storageUrl)
				.defaultHeader("apikey", apiKey)
				.requestFactory(requestFactory);
		if (apiKey.startsWith("eyJ")) {
			builder.defaultHeaders(headers -> headers.setBearerAuth(apiKey));
		}
		return builder.build();
	}

	public static void upload(RestClient client, String bucket, String key, byte[] content, String contentType) {
		client.post().uri(builder -> builder.pathSegment("object", bucket).pathSegment(key.split("/")).build())
				.contentType(MediaType.parseMediaType(contentType))
				.header("x-upsert", "false")
				.body(content).retrieve().toBodilessEntity();
	}

	public static void delete(RestClient client, String bucket, String key) {
		client.method(HttpMethod.DELETE)
				.uri("/object/{bucket}", bucket).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("prefixes", List.of(key))).retrieve().toBodilessEntity();
	}
}

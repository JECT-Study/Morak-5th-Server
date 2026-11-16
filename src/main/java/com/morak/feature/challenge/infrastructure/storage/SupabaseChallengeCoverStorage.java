package com.morak.feature.challenge.infrastructure.storage;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.port.out.ChallengeCoverStorage;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "morak.challenge.cover-storage.enabled", havingValue = "true")
class SupabaseChallengeCoverStorage implements ChallengeCoverStorage {

	private final RestClient client;
	private final String bucket;

	SupabaseChallengeCoverStorage(@Value("${morak.challenge.cover-storage.project-url}") String projectUrl,
			@Value("${morak.challenge.cover-storage.api-key}") String apiKey,
			@Value("${morak.challenge.cover-storage.bucket}") String bucket) {
		String storageUrl = projectUrl.replaceFirst("/+$", "") + "/storage/v1";
		this.bucket = bucket;
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
		this.client = builder.build();
	}

	@Override
	public void upload(String key, byte[] content, String contentType) {
		try {
			client.post().uri(builder -> builder.pathSegment("object", bucket).pathSegment(key.split("/")).build())
					.contentType(MediaType.parseMediaType(contentType))
					.header("x-upsert", "false")
					.body(content).retrieve().toBodilessEntity();
		} catch (RestClientException exception) {
			throw storageFailure();
		}
	}

	@Override
	public void delete(String key) {
		try {
			client.method(HttpMethod.DELETE)
					.uri("/object/{bucket}", bucket).contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("prefixes", List.of(key))).retrieve().toBodilessEntity();
		} catch (RestClientException exception) {
			throw storageFailure();
		}
	}

	private static BusinessException storageFailure() {
		return new BusinessException(ChallengeErrorCode.COVER_STORAGE_UNAVAILABLE);
	}
}

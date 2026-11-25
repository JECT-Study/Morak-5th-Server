package com.morak.feature.certification.infrastructure.storage;

import com.morak.common.exception.BusinessException;
import com.morak.feature.certification.application.port.out.CertificationImageStorage;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
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

/**
 * Supabase Storage 비공개 버킷에 인증 사진을 올리고 지우고, 열람용 서명 주소를 발급한다.
 *
 * <p>TODO(D-34): 서명 주소 유효 시간은 대표 사진과 같은 300초다.
 */
@Component
@ConditionalOnProperty(name = "morak.certification.image-storage.enabled", havingValue = "true")
class SupabaseCertificationImageStorage implements CertificationImageStorage {

	private final RestClient client;
	private final String storageUrl;
	private final String bucket;

	SupabaseCertificationImageStorage(@Value("${morak.certification.image-storage.project-url}") String projectUrl,
			@Value("${morak.certification.image-storage.api-key}") String apiKey,
			@Value("${morak.certification.image-storage.bucket}") String bucket) {
		this.storageUrl = projectUrl.replaceFirst("/+$", "") + "/storage/v1";
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

	@Override
	public Map<String, String> signDownloads(List<String> keys) {
		try {
			SignedObject[] objects = client.post().uri("/object/sign/{bucket}", bucket)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("paths", keys, "expiresIn", 300)).retrieve().body(SignedObject[].class);
			Map<String, String> urls = new HashMap<>();
			if (objects != null) {
				for (SignedObject object : objects) {
					if (object.signedURL() != null && object.signedURL().startsWith("/object/sign/")) {
						urls.put(object.path(), storageUrl + object.signedURL());
					}
				}
			}
			return urls;
		} catch (RestClientException exception) {
			throw storageFailure();
		}
	}

	private static BusinessException storageFailure() {
		return new BusinessException(CertificationErrorCode.IMAGE_STORAGE_UNAVAILABLE);
	}

	private record SignedObject(String path, String signedURL) {}
}

package com.morak.feature.challenge.infrastructure.storage;

import com.morak.common.exception.BusinessException;
import com.morak.common.storage.SupabaseStorageRequests;
import com.morak.feature.challenge.application.port.out.ChallengeCoverStorage;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "morak.challenge.cover-storage.enabled", havingValue = "true")
class SupabaseChallengeCoverStorage implements ChallengeCoverStorage {

	private final RestClient client;
	private final String storageUrl;
	private final String bucket;

	SupabaseChallengeCoverStorage(@Value("${morak.challenge.cover-storage.project-url}") String projectUrl,
			@Value("${morak.challenge.cover-storage.api-key}") String apiKey,
			@Value("${morak.challenge.cover-storage.bucket}") String bucket) {
		this.storageUrl = projectUrl.replaceFirst("/+$", "") + "/storage/v1";
		this.bucket = bucket;
		this.client = SupabaseStorageRequests.createClient(storageUrl, apiKey);
	}

	@Override
	public void upload(String key, byte[] content, String contentType) {
		try {
			SupabaseStorageRequests.upload(client, bucket, key, content, contentType);
		} catch (RestClientException exception) {
			throw storageFailure();
		}
	}

	@Override
	public void delete(String key) {
		try {
			SupabaseStorageRequests.delete(client, bucket, key);
		} catch (RestClientException exception) {
			throw storageFailure();
		}
	}

	@Override
	public Map<String, String> signDownloads(List<String> keys) {
		try {
			return SupabaseStorageRequests.sign(client, storageUrl, bucket, keys);
		} catch (RestClientException exception) {
			throw storageFailure();
		}
	}

	private static BusinessException storageFailure() {
		return new BusinessException(ChallengeErrorCode.COVER_STORAGE_UNAVAILABLE);
	}
}

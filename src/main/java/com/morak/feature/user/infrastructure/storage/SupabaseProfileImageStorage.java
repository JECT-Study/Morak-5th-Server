package com.morak.feature.user.infrastructure.storage;

import com.morak.common.exception.BusinessException;
import com.morak.common.storage.SupabaseStorageRequests;
import com.morak.feature.user.application.port.out.ProfileImageStorage;
import com.morak.feature.user.domain.exception.UserErrorCode;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 공개 버킷. 업로드·삭제는 서버 키로 하고, 읽기는 공개 URL로 한다(D-33). */
@Component
@ConditionalOnProperty(name = "morak.user.profile-image-storage.enabled", havingValue = "true")
class SupabaseProfileImageStorage implements ProfileImageStorage {

	private final RestClient client;
	private final String bucket;
	private final String publicUrlPrefix;

	SupabaseProfileImageStorage(@Value("${morak.user.profile-image-storage.project-url}") String projectUrl,
			@Value("${morak.user.profile-image-storage.api-key}") String apiKey,
			@Value("${morak.user.profile-image-storage.bucket}") String bucket) {
		String storageUrl = projectUrl.replaceFirst("/+$", "") + "/storage/v1";
		this.bucket = bucket;
		this.publicUrlPrefix = storageUrl + "/object/public/" + bucket + "/";
		this.client = SupabaseStorageRequests.createClient(storageUrl, apiKey);
	}

	@Override
	public String upload(String key, byte[] content, String contentType) {
		try {
			SupabaseStorageRequests.upload(client, bucket, key, content, contentType);
		} catch (RestClientException exception) {
			throw storageFailure();
		}
		return publicUrlPrefix + key;
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
	public Optional<String> keyOf(String url) {
		return url.startsWith(publicUrlPrefix)
				? Optional.of(url.substring(publicUrlPrefix.length()))
				: Optional.empty();
	}

	private static BusinessException storageFailure() {
		return new BusinessException(UserErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE);
	}
}

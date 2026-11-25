package com.morak.feature.certification.infrastructure.storage;

import com.morak.common.exception.BusinessException;
import com.morak.common.storage.SupabaseStorageRequests;
import com.morak.feature.certification.application.port.out.CertificationImageStorage;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
		return new BusinessException(CertificationErrorCode.IMAGE_STORAGE_UNAVAILABLE);
	}
}

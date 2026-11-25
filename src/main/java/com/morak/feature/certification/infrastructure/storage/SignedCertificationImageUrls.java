package com.morak.feature.certification.infrastructure.storage;

import com.morak.common.exception.BusinessException;
import com.morak.feature.certification.application.port.out.CertificationImageStorage;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 인증 사진 키를 비공개 버킷의 서명 주소로 바꾼다. 저장소가 없거나 서명에 실패하면 빈 목록이다(대표 사진과 같음).
 *
 * <p>TODO(D-34): 비공개 버킷 + 서명 주소(300초)는 제안값이다.
 */
@Component
class SignedCertificationImageUrls implements CertificationImageUrls {

	private static final Logger log = LoggerFactory.getLogger(SignedCertificationImageUrls.class);

	private final Optional<CertificationImageStorage> storage;

	SignedCertificationImageUrls(Optional<CertificationImageStorage> storage) {
		this.storage = storage;
	}

	@Override
	public List<String> resolve(List<String> imageKeys) {
		if (imageKeys.isEmpty() || storage.isEmpty()) {
			return List.of();
		}
		Map<String, String> urls;
		try {
			urls = storage.get().signDownloads(imageKeys);
		} catch (BusinessException exception) {
			log.warn("Could not sign certification image URLs");
			return List.of();
		}
		return imageKeys.stream().map(urls::get).filter(Objects::nonNull).toList();
	}
}

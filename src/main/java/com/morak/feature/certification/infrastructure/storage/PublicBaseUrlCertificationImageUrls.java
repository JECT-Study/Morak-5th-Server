package com.morak.feature.certification.infrastructure.storage;

import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인증 사진 저장 키를 {@code morak.certification.image-base-url} 뒤에 붙여 주소로 만든다.
 * 주소가 설정되지 않으면 열람 가능한 주소가 없는 것으로 보고 빈 목록을 돌려준다.
 *
 * <p>TODO: 인증 사진 업로드·비공개 버킷(서명 URL) 방식이 정해지면 {@code SupabaseChallengeCoverStorage}처럼 서명 주소로 교체한다.
 */
@Component
class PublicBaseUrlCertificationImageUrls implements CertificationImageUrls {

	private final String baseUrl;

	PublicBaseUrlCertificationImageUrls(@Value("${morak.certification.image-base-url:}") String baseUrl) {
		this.baseUrl = baseUrl.strip().replaceFirst("/+$", "");
	}

	@Override
	public List<String> resolve(List<String> imageKeys) {
		if (baseUrl.isEmpty()) {
			return List.of();
		}
		return imageKeys.stream().map(key -> baseUrl + "/" + key.replaceFirst("^/+", "")).toList();
	}
}

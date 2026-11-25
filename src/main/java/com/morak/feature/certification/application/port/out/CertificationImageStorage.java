package com.morak.feature.certification.application.port.out;

import java.util.List;
import java.util.Map;

/** 인증 사진 저장소(비공개 버킷). 설정으로 켜졌을 때만 빈이 있다. */
public interface CertificationImageStorage {

	void upload(String key, byte[] content, String contentType);

	void delete(String key);

	/** key -> 짧게 유효한 서명 주소. 서명하지 못한 키는 결과에 없다. */
	Map<String, String> signDownloads(List<String> keys);
}

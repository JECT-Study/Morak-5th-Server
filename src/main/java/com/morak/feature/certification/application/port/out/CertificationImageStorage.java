package com.morak.feature.certification.application.port.out;

/** 인증 사진 저장소. 설정으로 켜졌을 때만 빈이 있다. */
public interface CertificationImageStorage {

	void upload(String key, byte[] content, String contentType);

	void delete(String key);
}

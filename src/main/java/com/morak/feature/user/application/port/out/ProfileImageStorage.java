package com.morak.feature.user.application.port.out;

import java.util.Optional;

/** 프로필 사진 저장소. 공개 버킷이라 저장한 URL을 그대로 내려준다(D-33). */
public interface ProfileImageStorage {

	/** @return 누구나 읽을 수 있는 사진 URL */
	String upload(String key, byte[] content, String contentType);

	void delete(String key);

	/** 이 저장소에 올린 사진의 URL이면 객체 키를 돌려준다. 소셜 사진 URL이면 비어 있다. */
	Optional<String> keyOf(String url);
}

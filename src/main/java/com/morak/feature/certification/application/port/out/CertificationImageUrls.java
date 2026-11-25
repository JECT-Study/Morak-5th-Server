package com.morak.feature.certification.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 인증 사진 저장 키를 열람 가능한 주소로 바꾼다. */
public interface CertificationImageUrls {

	/** key -> 주소. 주소를 만들 수 없는 키(저장소 미설정·서명 실패)는 결과에 없다. */
	Map<String, String> resolveByKey(List<String> imageKeys);

	/** 키 순서대로의 주소. 주소를 만들 수 없는 키는 결과에서 빠진다. */
	default List<String> resolve(List<String> imageKeys) {
		Map<String, String> urls = resolveByKey(imageKeys);
		return imageKeys.stream().map(urls::get).filter(Objects::nonNull).toList();
	}
}

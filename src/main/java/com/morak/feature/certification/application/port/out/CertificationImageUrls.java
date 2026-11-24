package com.morak.feature.certification.application.port.out;

import java.util.List;

/** 인증 사진 저장 키를 열람 가능한 주소로 바꾼다. */
public interface CertificationImageUrls {

	/** 주소를 만들 수 없는 키(저장소 미설정 등)는 결과에서 빠진다. */
	List<String> resolve(List<String> imageKeys);
}

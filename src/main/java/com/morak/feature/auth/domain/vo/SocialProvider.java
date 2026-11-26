package com.morak.feature.auth.domain.vo;

import com.morak.feature.auth.domain.exception.AuthErrorCode;

public enum SocialProvider {
	KAKAO(AuthErrorCode.INVALID_KAKAO_AUTHORIZATION, AuthErrorCode.KAKAO_UNAVAILABLE),
	GOOGLE(AuthErrorCode.INVALID_GOOGLE_AUTHORIZATION, AuthErrorCode.GOOGLE_UNAVAILABLE),
	// 로컬 전용 provider. 별도 에러 코드 없이 기존처럼 카카오 코드를 쓴다.
	DEV(AuthErrorCode.INVALID_KAKAO_AUTHORIZATION, AuthErrorCode.KAKAO_UNAVAILABLE);

	private final AuthErrorCode invalidAuthorization;
	private final AuthErrorCode unavailable;

	SocialProvider(AuthErrorCode invalidAuthorization, AuthErrorCode unavailable) {
		this.invalidAuthorization = invalidAuthorization;
		this.unavailable = unavailable;
	}

	public AuthErrorCode invalidAuthorization() {
		return invalidAuthorization;
	}

	public AuthErrorCode unavailable() {
		return unavailable;
	}
}

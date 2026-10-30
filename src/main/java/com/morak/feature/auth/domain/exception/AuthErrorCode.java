package com.morak.feature.auth.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
	INVALID_KAKAO_AUTHORIZATION("AUTH_001", "카카오 로그인 인증 코드가 유효하지 않습니다.", HttpStatus.UNAUTHORIZED),
	KAKAO_UNAVAILABLE("AUTH_002", "카카오 로그인 처리 중 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
	WITHDRAWN_USER("AUTH_003", "탈퇴 처리된 계정입니다.", HttpStatus.FORBIDDEN),
	AUTHENTICATION_REQUIRED("AUTH_004", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED),
	ACCESS_DENIED("AUTH_005", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN);

	private final String code;
	private final String message;
	private final HttpStatus status;

	AuthErrorCode(String code, String message, HttpStatus status) {
		this.code = code;
		this.message = message;
		this.status = status;
	}

	@Override public String code() { return code; }
	@Override public String message() { return message; }
	@Override public HttpStatus status() { return status; }
}

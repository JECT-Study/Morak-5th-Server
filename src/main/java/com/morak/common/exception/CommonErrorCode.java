package com.morak.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
	INVALID_REQUEST("COMMON_001", "요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
	RESOURCE_NOT_FOUND("COMMON_002", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	INTERNAL_SERVER_ERROR("COMMON_003", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String code;
	private final String message;
	private final HttpStatus status;

	CommonErrorCode(String code, String message, HttpStatus status) {
		this.code = code;
		this.message = message;
		this.status = status;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public HttpStatus status() {
		return status;
	}
}

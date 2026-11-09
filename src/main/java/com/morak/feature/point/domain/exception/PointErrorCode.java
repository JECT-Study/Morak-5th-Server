package com.morak.feature.point.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PointErrorCode implements ErrorCode {
	INVALID_AMOUNT("POINT_001", "포인트 금액은 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
	INSUFFICIENT_BALANCE("POINT_002", "포인트 잔액이 부족합니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String message;
	private final HttpStatus status;

	PointErrorCode(String code, String message, HttpStatus status) {
		this.code = code;
		this.message = message;
		this.status = status;
	}

	@Override public String code() { return code; }
	@Override public String message() { return message; }
	@Override public HttpStatus status() { return status; }
}

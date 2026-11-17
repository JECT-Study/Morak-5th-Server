package com.morak.feature.notification.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements ErrorCode {
	INVALID_COMMAND("NOTIFICATION_001", "알림 요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String message;
	private final HttpStatus status;

	NotificationErrorCode(String code, String message, HttpStatus status) {
		this.code = code;
		this.message = message;
		this.status = status;
	}

	@Override public String code() { return code; }
	@Override public String message() { return message; }
	@Override public HttpStatus status() { return status; }
}

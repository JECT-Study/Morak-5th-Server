package com.morak.feature.notification.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements ErrorCode {
	INVALID_COMMAND("NOTIFICATION_001", "알림 요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
	INVALID_CURSOR("NOTIFICATION_002", "before는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
	/** 없는 알림과 다른 사람의 알림을 구분하지 않는다. 남의 알림 ID가 존재하는지 알려 주지 않기 위해서다. */
	NOTIFICATION_NOT_FOUND("NOTIFICATION_003", "알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	INVALID_PUSH_TOKEN("NOTIFICATION_004", "푸시 토큰 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);

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

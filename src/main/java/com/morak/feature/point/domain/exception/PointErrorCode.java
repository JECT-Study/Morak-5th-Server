package com.morak.feature.point.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PointErrorCode implements ErrorCode {
	INVALID_AMOUNT("POINT_001", "포인트 금액은 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
	INSUFFICIENT_BALANCE("POINT_002", "포인트 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
	MISSING_REQUIRED_VALUE("POINT_003", "포인트 처리에 필요한 값이 없습니다.", HttpStatus.BAD_REQUEST),
	INVALID_IDEMPOTENCY_KEY("POINT_004", "중복 처리 방지 키는 1자 이상 100자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
	INVALID_CHALLENGE_TITLE("POINT_005", "챌린지 이름은 1자 이상 50자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
	IDEMPOTENCY_KEY_CONFLICT("POINT_006", "같은 키로 다른 내용의 포인트 거래가 이미 처리되었습니다.", HttpStatus.CONFLICT),
	DEPOSIT_NOT_FOUND("POINT_007", "환급할 보증금 납부 내역이 없습니다.", HttpStatus.CONFLICT),
	REFUND_EXCEEDS_DEPOSIT("POINT_008", "환급 금액이 납부한 보증금보다 큽니다.", HttpStatus.BAD_REQUEST),
	INVALID_CURSOR("POINT_009", "before는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST);

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

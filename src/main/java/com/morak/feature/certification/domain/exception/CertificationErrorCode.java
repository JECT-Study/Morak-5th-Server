package com.morak.feature.certification.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CertificationErrorCode implements ErrorCode {
	REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "검수할 인증을 찾을 수 없습니다."),
	ALREADY_JUDGED(HttpStatus.CONFLICT, "이미 처리된 인증입니다. 최신 결과를 확인해 주세요."),
	REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "거절 사유를 입력해 주세요."),
	REJECT_REASON_TOO_LONG(HttpStatus.BAD_REQUEST, "거절 사유는 200자 이하로 입력해 주세요.");

	private final HttpStatus status;
	private final String message;

	CertificationErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public String code() { return "CERTIFICATION_" + name(); }

	@Override
	public String message() { return message; }

	@Override
	public HttpStatus status() { return status; }
}

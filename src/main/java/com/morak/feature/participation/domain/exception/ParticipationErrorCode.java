package com.morak.feature.participation.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ParticipationErrorCode implements ErrorCode {
	INVALID_STATUS_TRANSITION("PARTICIPATION_001", "현재 참가 상태에서는 처리할 수 없습니다.", HttpStatus.CONFLICT),
	ALREADY_APPLIED("PARTICIPATION_002", "이미 신청했거나 참여 중인 챌린지입니다.", HttpStatus.CONFLICT);

	private final String code;
	private final String message;
	private final HttpStatus status;

	ParticipationErrorCode(String code, String message, HttpStatus status) {
		this.code = code;
		this.message = message;
		this.status = status;
	}

	@Override public String code() { return code; }
	@Override public String message() { return message; }
	@Override public HttpStatus status() { return status; }
}

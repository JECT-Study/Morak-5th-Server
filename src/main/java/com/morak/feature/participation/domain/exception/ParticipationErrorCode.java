package com.morak.feature.participation.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ParticipationErrorCode implements ErrorCode {
	INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "현재 참가 상태에서는 처리할 수 없습니다."),
	ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 신청했거나 참여 중인 챌린지입니다.");

	private final HttpStatus status;
	private final String message;

	ParticipationErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public String code() { return "PARTICIPATION_" + name(); }

	@Override
	public String message() { return message; }

	@Override
	public HttpStatus status() { return status; }
}

package com.morak.feature.participation.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ParticipationErrorCode implements ErrorCode {
	INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "현재 참가 상태에서는 처리할 수 없습니다."),
	ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 신청했거나 참여 중인 챌린지입니다."),
	APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "신청을 찾을 수 없습니다."),
	NOT_CHALLENGE_HOST(HttpStatus.FORBIDDEN, "방장만 신청을 처리할 수 있습니다."),
	INVALID_INVITE_CODE(HttpStatus.FORBIDDEN, "초대 코드가 올바르지 않습니다."),
	CHALLENGE_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 챌린지가 아닙니다."),
	CHALLENGE_FULL(HttpStatus.CONFLICT, "정원이 가득 찼습니다."),
	PARTICIPATION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "참여 가능한 챌린지를 초과했습니다");

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

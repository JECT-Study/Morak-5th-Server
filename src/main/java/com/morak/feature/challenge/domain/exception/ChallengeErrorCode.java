package com.morak.feature.challenge.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ChallengeErrorCode implements ErrorCode {
	INVALID_PERIOD(HttpStatus.BAD_REQUEST, "챌린지 기간은 최소 7일이어야 합니다."),
	INVALID_START_DATE(HttpStatus.BAD_REQUEST, "시작일은 내일부터 선택할 수 있습니다."),
	DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "시작일 또는 종료일이 저장 가능한 날짜 범위를 벗어났습니다."),
	INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "공개 여부에 맞는 챌린지 정원을 입력해 주세요."),
	INVALID_POINT_AMOUNT(HttpStatus.BAD_REQUEST, "참여 포인트는 공개 챌린지에만 1,000~5,000P로 설정할 수 있습니다."),
	PARTICIPATION_LIMIT(HttpStatus.CONFLICT, "참여 가능한 챌린지를 초과했습니다"),
	POINTS_NOT_SUPPORTED(HttpStatus.CONFLICT, "현재는 참여 포인트 없이만 개설할 수 있습니다."),
	PENDING_POINTS_NOT_SUPPORTED(HttpStatus.CONFLICT, "참여 포인트가 걸린 대기 신청을 처리할 수 없어 개설할 수 없습니다."),
	INVITE_CODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "초대 코드를 발급하지 못했습니다. 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;

	ChallengeErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public String code() { return "CHALLENGE_" + name(); }

	@Override
	public String message() { return message; }

	@Override
	public HttpStatus status() { return status; }
}

package com.morak.feature.challenge.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ChallengeErrorCode implements ErrorCode {
	INVALID_PERIOD(HttpStatus.BAD_REQUEST, "챌린지 기간은 최소 7일이어야 합니다."),
	INVALID_START_DATE(HttpStatus.BAD_REQUEST, "시작일은 내일부터 선택할 수 있습니다."),
	DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "시작일 또는 종료일이 저장 가능한 날짜 범위를 벗어났습니다."),
	INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "공개 여부에 맞는 챌린지 정원을 입력해 주세요."),
	INVALID_COVER_IMAGE(HttpStatus.BAD_REQUEST, "JPEG, PNG, WebP 사진을 선택해 주세요."),
	COVER_IMAGE_TOO_LARGE(HttpStatus.valueOf(413), "대표 사진은 5MB 이하로 줄여 주세요."),
	COVER_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "사진을 처리할 수 없습니다. 다시 시도해 주세요."),
	INVALID_POINT_AMOUNT(HttpStatus.BAD_REQUEST, "참여 포인트는 0P 또는 공개 챌린지에서 1,000~5,000P를 500P 단위로 설정할 수 있습니다."),
	PARTICIPATION_LIMIT(HttpStatus.CONFLICT, "참여 가능한 챌린지를 초과했습니다"),
	PENDING_POINTS_NOT_SUPPORTED(HttpStatus.CONFLICT, "참여 포인트가 걸린 대기 신청을 처리할 수 없어 개설할 수 없습니다."),
	CREATION_INTEGRATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "챌린지 개설 기능을 준비 중입니다."),
	RESCHEDULE_INTEGRATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "챌린지 기간 재설정 기능을 준비 중입니다."),
	INVITE_CODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "초대 코드를 발급하지 못했습니다. 다시 시도해 주세요."),
	PRIVATE_CHALLENGE_FORBIDDEN(HttpStatus.FORBIDDEN, "비공개 챌린지를 조회할 권한이 없습니다."),
	NOT_OWNER(HttpStatus.FORBIDDEN, "방장만 처리할 수 있습니다."),
	NOT_START_HELD(HttpStatus.CONFLICT, "시작 보류 중인 챌린지만 처리할 수 있습니다."),
	DISCARD_APPLICATIONS_CHANGED(HttpStatus.CONFLICT, "폐기하는 동안 참가 신청이 바뀌었습니다. 다시 시도해 주세요."),
	DISCARD_INTEGRATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "챌린지 폐기 기능을 준비 중입니다."),
	NOT_STARTABLE(HttpStatus.CONFLICT, "시작할 수 없는 상태의 챌린지입니다."),
	NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 챌린지만 처리할 수 있습니다."),
	START_APPLICATIONS_CHANGED(HttpStatus.CONFLICT, "시작 처리 중 참가 신청이 바뀌었습니다. 다시 시도해 주세요."),
	START_INTEGRATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "챌린지 시작 처리 기능을 준비 중입니다."),
	INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "유효하지 않은 코드입니다"),
	ALREADY_ENDED(HttpStatus.GONE, "이미 종료된 챌린지입니다"),
	// TODO(D-40): 문구는 PM 확인 전 제안값이다.
	INVITE_CODE_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "코드를 여러 번 잘못 입력했습니다. 잠시 후 다시 시도해 주세요.");

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

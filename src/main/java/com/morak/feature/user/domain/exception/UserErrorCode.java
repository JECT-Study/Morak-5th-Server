package com.morak.feature.user.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
	TERMS_ALREADY_AGREED("USER_001", "이미 필수 약관에 동의했습니다."),
	TERMS_AGREEMENT_REQUIRED("USER_002", "필수 약관에 먼저 동의해야 합니다."),
	ALREADY_ONBOARDED("USER_003", "이미 온보딩을 완료했습니다."),
	NICKNAME_ALREADY_IN_USE("USER_004", "이미 사용 중인 닉네임입니다."),
	// TODO(D-3): 안내 문구는 PM 확인 전 임시값이다.
	WITHDRAWAL_NOT_ALLOWED("USER_005", "진행 중이거나 시작을 기다리는 챌린지가 있어 탈퇴할 수 없습니다."),
	// TODO(D-33): 프로필 사진 문구는 대표 사진(D-17)과 맞춘 임시값이다.
	INVALID_PROFILE_IMAGE("USER_006", "JPEG, PNG, WebP 사진을 선택해 주세요.", HttpStatus.BAD_REQUEST),
	PROFILE_IMAGE_TOO_LARGE("USER_007", "프로필 사진은 5MB 이하로 줄여 주세요.", HttpStatus.valueOf(413)),
	PROFILE_IMAGE_STORAGE_UNAVAILABLE("USER_008", "사진을 처리할 수 없습니다. 다시 시도해 주세요.",
			HttpStatus.SERVICE_UNAVAILABLE);

	private final String code;
	private final String message;
	private final HttpStatus status;

	UserErrorCode(String code, String message) {
		this(code, message, HttpStatus.CONFLICT);
	}

	UserErrorCode(String code, String message, HttpStatus status) {
		this.code = code;
		this.message = message;
		this.status = status;
	}

	@Override
	public String code() { return code; }

	@Override
	public String message() { return message; }

	@Override
	public HttpStatus status() { return status; }
}

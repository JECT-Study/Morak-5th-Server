package com.morak.feature.user.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
	TERMS_ALREADY_AGREED("USER_001", "이미 필수 약관에 동의했습니다."),
	TERMS_AGREEMENT_REQUIRED("USER_002", "필수 약관에 먼저 동의해야 합니다."),
	ALREADY_ONBOARDED("USER_003", "이미 온보딩을 완료했습니다."),
	NICKNAME_ALREADY_IN_USE("USER_004", "이미 사용 중인 닉네임입니다."),
	// TODO(D-3): 안내 문구는 PM 확인 전 임시값이다.
	WITHDRAWAL_NOT_ALLOWED("USER_005", "진행 중이거나 시작을 기다리는 챌린지가 있어 탈퇴할 수 없습니다.");

	private final String code;
	private final String message;

	UserErrorCode(String code, String message) {
		this.code = code;
		this.message = message;
	}

	@Override
	public String code() { return code; }

	@Override
	public String message() { return message; }

	@Override
	public HttpStatus status() { return HttpStatus.CONFLICT; }
}

package com.morak.common.exception;

public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	/** 로그에만 남기는 상황 설명 (요청 값, 판단 근거 등). 응답에는 내보내지 않는다. */
	private final String detail;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, null, null);
	}

	public BusinessException(ErrorCode errorCode, String detail) {
		this(errorCode, detail, null);
	}

	public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
		super(errorCode.message(), cause);
		this.errorCode = errorCode;
		this.detail = detail;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public String getDetail() {
		return detail;
	}
}

package com.morak.common.exception;

import java.time.Duration;

/** 요청 횟수 제한으로 거절한 업무 예외. 응답에 {@code Retry-After} 헤더(초)를 함께 내려준다. */
public class RateLimitedException extends BusinessException {

	private final Duration retryAfter;

	public RateLimitedException(ErrorCode errorCode, Duration retryAfter, String detail) {
		super(errorCode, detail);
		this.retryAfter = retryAfter;
	}

	/** 다시 시도할 수 있을 때까지 남은 시간을 초 단위로 올림한다. 최소 1초. */
	public long retryAfterSeconds() {
		long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
		return Math.max(1, seconds);
	}
}

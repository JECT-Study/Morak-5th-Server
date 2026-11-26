package com.morak.feature.ad.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AdErrorCode implements ErrorCode {
	INVALID_TOKEN(HttpStatus.BAD_REQUEST, "광고 시청 확인값이 올바르지 않습니다."),
	EXPIRED_TOKEN(HttpStatus.BAD_REQUEST, "광고 시청 확인 시간이 지났습니다. 다시 시도해 주세요."),
	WATCH_TIME_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "광고를 끝까지 시청해 주세요."),
	// TODO(D-8): 한도 초과를 409로 응답한다. 클라이언트와 PM 확인 후 확정한다.
	DAILY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "오늘은 광고 보상을 모두 받았습니다."),
	UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "광고 보상 기능을 사용할 수 없습니다.");

	private final HttpStatus status;
	private final String message;

	AdErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public String code() { return "AD_" + name(); }

	@Override
	public String message() { return message; }

	@Override
	public HttpStatus status() { return status; }
}

package com.morak.feature.ad.domain.vo;

import java.time.Duration;
import java.time.Instant;

/**
 * 광고 시청 시작 때 발급하는 1회용 토큰의 내용. id가 포인트 지급 멱등키가 되어 한 번만 쓸 수 있다.
 */
public record AdToken(String id, long userId, Instant issuedAt, Instant expiresAt) {

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	/** 발급 후 minWatch 이상 지났는지. */
	public boolean watchedAtLeast(Instant now, Duration minWatch) {
		return !now.isBefore(issuedAt.plus(minWatch));
	}
}

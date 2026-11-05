package com.morak.feature.auth.domain.vo;

public record AuthUserId(long value) {
	public AuthUserId {
		if (value <= 0) {
			throw new IllegalArgumentException("User ID must be positive");
		}
	}
}

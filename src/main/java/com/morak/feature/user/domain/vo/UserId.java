package com.morak.feature.user.domain.vo;

public record UserId(Long value) {
	public UserId {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException("User ID must be positive");
		}
	}
}

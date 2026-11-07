package com.morak.feature.auth.application.service;

import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenCleanupService {
	private final RefreshTokenRepository refreshTokens;
	private final Duration retention;

	/** Accepts zero retention for immediate expired-token cleanup, but rejects negative durations. */
	public RefreshTokenCleanupService(RefreshTokenRepository refreshTokens,
			@Value("${morak.auth.refresh-token-cleanup.retention:P7D}") Duration retention) {
		if (retention.isNegative()) {
			throw new IllegalStateException("Refresh token retention must not be negative");
		}
		this.refreshTokens = refreshTokens;
		this.retention = retention;
	}

	/**
	 * Deletes only tokens expired for at least the configured retention period.
	 * Revoked tokens that have not expired remain available for reuse rejection and inspection.
	 * Uses the same local time basis as token issuance and expiration checks.
	 */
	@Transactional
	public int cleanup() {
		return refreshTokens.deleteExpiredBefore(LocalDateTime.now().minus(retention));
	}
}

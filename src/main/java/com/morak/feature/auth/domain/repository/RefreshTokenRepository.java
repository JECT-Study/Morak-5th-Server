package com.morak.feature.auth.domain.repository;

import com.morak.feature.auth.domain.entity.RefreshToken;
import java.util.Optional;
import java.time.LocalDateTime;

public interface RefreshTokenRepository {

	RefreshToken save(RefreshToken refreshToken);

	/** Must be called within a transaction that also performs token rotation or revocation. */
	Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

	/** Deletes tokens whose expiry is at or before the retention cutoff. Requires a write transaction. */
	int deleteExpiredBefore(LocalDateTime cutoff);
}

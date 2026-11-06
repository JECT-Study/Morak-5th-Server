package com.morak.feature.auth.domain.repository;

import com.morak.feature.auth.domain.entity.RefreshToken;
import java.util.Optional;

public interface RefreshTokenRepository {

	RefreshToken save(RefreshToken refreshToken);

	/** Must be called within a transaction that also performs token rotation or revocation. */
	Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);
}

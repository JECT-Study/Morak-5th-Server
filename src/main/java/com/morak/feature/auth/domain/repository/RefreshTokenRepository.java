package com.morak.feature.auth.domain.repository;

import com.morak.feature.auth.domain.entity.RefreshToken;

public interface RefreshTokenRepository {

	RefreshToken save(RefreshToken refreshToken);
}

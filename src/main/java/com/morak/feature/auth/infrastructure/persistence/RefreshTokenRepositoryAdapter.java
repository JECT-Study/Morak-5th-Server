package com.morak.feature.auth.infrastructure.persistence;

import com.morak.feature.auth.domain.entity.RefreshToken;
import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

	private final JpaRefreshTokenRepository jpaRepository;

	public RefreshTokenRepositoryAdapter(JpaRefreshTokenRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public RefreshToken save(RefreshToken refreshToken) { return jpaRepository.save(refreshToken); }
}

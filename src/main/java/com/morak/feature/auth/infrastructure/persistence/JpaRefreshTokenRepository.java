package com.morak.feature.auth.infrastructure.persistence;

import com.morak.feature.auth.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshToken, Long> {}

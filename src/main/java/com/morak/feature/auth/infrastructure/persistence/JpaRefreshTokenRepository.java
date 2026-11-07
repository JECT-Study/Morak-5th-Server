package com.morak.feature.auth.infrastructure.persistence;

import com.morak.feature.auth.domain.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	@Modifying
	@Query("delete from RefreshToken token where token.expiresAt <= :cutoff")
	int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}

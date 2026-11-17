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

	/**
	 * 벌크 UPDATE라 이미 로드된 RefreshToken 엔티티에는 반영되지 않는다. 같은 트랜잭션에서 토큰을 먼저 조회했다면 refresh()로 다시 읽는다.
	 * clearAutomatically는 쓰지 않는다. 같은 트랜잭션에서 잠가 둔 User 등 다른 엔티티까지 떨어져 나가 변경이 사라진다.
	 */
	@Modifying(flushAutomatically = true)
	@Query("update RefreshToken token set token.revokedAt = :now where token.userId = :userId and token.revokedAt is null")
	int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

	@Modifying
	@Query("delete from RefreshToken token where token.expiresAt <= :cutoff")
	int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}

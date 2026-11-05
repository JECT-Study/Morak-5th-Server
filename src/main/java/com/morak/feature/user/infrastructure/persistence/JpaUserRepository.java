package com.morak.feature.user.infrastructure.persistence;

import com.morak.feature.user.domain.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface JpaUserRepository extends JpaRepository<User, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<User> findLockedById(Long id);

	boolean existsByNickname(String nickname);
}

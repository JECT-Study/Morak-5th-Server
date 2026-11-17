package com.morak.feature.challenge.infrastructure.persistence;

import com.morak.feature.challenge.domain.entity.Challenge;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface JpaChallengeRepository extends JpaRepository<Challenge, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Challenge c where c.id = :id")
	Optional<Challenge> findByIdForUpdate(long id);
}

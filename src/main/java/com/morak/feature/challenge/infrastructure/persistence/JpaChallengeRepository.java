package com.morak.feature.challenge.infrastructure.persistence;

import com.morak.feature.challenge.domain.entity.Challenge;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface JpaChallengeRepository extends JpaRepository<Challenge, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Challenge c where c.id = :id")
	Optional<Challenge> findByIdForUpdate(long id);

	/** 삭제된 챌린지는 엔티티에 매핑돼 있지 않은 deleted_at으로 거르므로 네이티브 쿼리로 읽는다. */
	@Query(value = """
			SELECT id FROM {h-schema}challenge
			WHERE deleted_at IS NULL AND id > :afterId
			AND ((status = 'RECRUITING' AND start_date <= :today) OR (status = 'START_HELD' AND COALESCE(start_held_on, start_date) < :today))
			ORDER BY id
			LIMIT :limit
			""", nativeQuery = true)
	List<Long> findStartDueIds(LocalDate today, long afterId, int limit);
}

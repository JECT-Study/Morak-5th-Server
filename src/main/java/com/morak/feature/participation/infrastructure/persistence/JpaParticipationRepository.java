package com.morak.feature.participation.infrastructure.persistence;

import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface JpaParticipationRepository extends JpaRepository<Participation, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select p from Participation p
			where p.userId = :userId and p.status = com.morak.feature.participation.domain.enums.ParticipationStatus.PENDING
			order by p.appliedAt, p.id
			""")
	List<Participation> findPendingByUserIdForUpdate(long userId);

	List<Participation> findByChallengeIdInAndRole(Collection<Long> challengeIds, ParticipationRole role);
}

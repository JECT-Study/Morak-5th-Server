package com.morak.feature.participation.infrastructure.persistence;

import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Participation p where p.id = :id")
	Optional<Participation> findByIdForUpdate(long id);

	List<Participation> findByChallengeIdInAndRole(Collection<Long> challengeIds, ParticipationRole role);

	@Query("""
			select p from Participation p
			where p.challengeId = :challengeId and p.status = com.morak.feature.participation.domain.enums.ParticipationStatus.PENDING
			order by p.appliedAt, p.id
			""")
	List<Participation> findPendingByChallenge(long challengeId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select p from Participation p
			where p.challengeId = :challengeId and p.status = com.morak.feature.participation.domain.enums.ParticipationStatus.PENDING
			order by p.appliedAt, p.id
			""")
	List<Participation> findPendingByChallengeForUpdate(long challengeId);

	@Query("""
			select count(p) > 0 from Participation p
			where p.challengeId = :challengeId
			and p.status = com.morak.feature.participation.domain.enums.ParticipationStatus.ACTIVE
			and p.role <> com.morak.feature.participation.domain.enums.ParticipationRole.HOST
			""")
	boolean existsActiveMemberExceptHost(long challengeId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select p from Participation p
			where p.challengeId = :challengeId
			and p.status = com.morak.feature.participation.domain.enums.ParticipationStatus.ACTIVE
			order by p.joinedAt, p.id
			""")
	List<Participation> findActiveByChallengeForUpdate(long challengeId);
}

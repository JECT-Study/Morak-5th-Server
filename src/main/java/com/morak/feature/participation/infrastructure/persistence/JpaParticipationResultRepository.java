package com.morak.feature.participation.infrastructure.persistence;

import com.morak.feature.participation.domain.entity.ParticipationResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface JpaParticipationResultRepository extends JpaRepository<ParticipationResult, Long> {

	@Query("select r.participationId from ParticipationResult r where r.participationId in :participationIds")
	List<Long> findExistingParticipationIds(Collection<Long> participationIds);
}

package com.morak.feature.participation.infrastructure.persistence;

import com.morak.feature.participation.domain.entity.ParticipationResult;
import com.morak.feature.participation.domain.repository.ParticipationResultRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
class ParticipationResultRepositoryAdapter implements ParticipationResultRepository {

	private final JpaParticipationResultRepository repository;

	ParticipationResultRepositoryAdapter(JpaParticipationResultRepository repository) {
		this.repository = repository;
	}

	@Override
	public ParticipationResult save(ParticipationResult result) {
		return repository.save(result);
	}

	@Override
	public Set<Long> findExistingParticipationIds(Collection<Long> participationIds) {
		if (participationIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(repository.findExistingParticipationIds(participationIds));
	}
}

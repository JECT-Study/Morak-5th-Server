package com.morak.feature.challenge.infrastructure.persistence;

import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import org.springframework.stereotype.Repository;

@Repository
class ChallengeRepositoryAdapter implements ChallengeRepository {

	private final JpaChallengeRepository repository;

	ChallengeRepositoryAdapter(JpaChallengeRepository repository) {
		this.repository = repository;
	}

	@Override
	public Challenge save(Challenge challenge) {
		return repository.saveAndFlush(challenge);
	}
}

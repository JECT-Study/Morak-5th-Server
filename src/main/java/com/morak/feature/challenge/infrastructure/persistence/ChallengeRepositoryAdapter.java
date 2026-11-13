package com.morak.feature.challenge.infrastructure.persistence;

import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.exception.InviteCodeConflictException;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class ChallengeRepositoryAdapter implements ChallengeRepository {

	private static final String INVITE_CODE_INDEX = "uk_challenge_invite_code";

	private final JpaChallengeRepository repository;

	ChallengeRepositoryAdapter(JpaChallengeRepository repository) {
		this.repository = repository;
	}

	@Override
	public Challenge save(Challenge challenge) {
		try {
			return repository.saveAndFlush(challenge);
		} catch (DataIntegrityViolationException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof ConstraintViolationException violation
						&& INVITE_CODE_INDEX.equals(violation.getConstraintName())) {
					throw new InviteCodeConflictException(exception);
				}
			}
			throw exception;
		}
	}
}

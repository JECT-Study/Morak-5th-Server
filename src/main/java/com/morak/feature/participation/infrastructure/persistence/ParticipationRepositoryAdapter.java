package com.morak.feature.participation.infrastructure.persistence;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.util.Collection;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class ParticipationRepositoryAdapter implements ParticipationRepository {

	private static final String CHALLENGE_USER_INDEX = "uk_participation_challenge_user";

	private final JpaParticipationRepository repository;

	ParticipationRepositoryAdapter(JpaParticipationRepository repository) {
		this.repository = repository;
	}

	@Override
	public Participation save(Participation participation) {
		try {
			return repository.saveAndFlush(participation);
		} catch (DataIntegrityViolationException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof ConstraintViolationException violation
						&& CHALLENGE_USER_INDEX.equals(violation.getConstraintName())) {
					throw new BusinessException(ParticipationErrorCode.ALREADY_APPLIED);
				}
			}
			throw exception;
		}
	}

	@Override
	public List<Participation> findPendingByUserIdForUpdate(long userId) {
		return repository.findPendingByUserIdForUpdate(userId);
	}

	@Override
	public List<Participation> findHosts(Collection<Long> challengeIds) {
		if (challengeIds.isEmpty()) {
			return List.of();
		}
		return repository.findByChallengeIdInAndRole(challengeIds, ParticipationRole.HOST);
	}
}

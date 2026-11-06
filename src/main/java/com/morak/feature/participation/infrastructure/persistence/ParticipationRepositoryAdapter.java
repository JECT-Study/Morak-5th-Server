package com.morak.feature.participation.infrastructure.persistence;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipationRepositoryAdapter implements ParticipationRepository {

	private final JpaParticipationRepository jpaRepository;

	public ParticipationRepositoryAdapter(JpaParticipationRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Participation save(Participation participation) {
		try {
			return jpaRepository.saveAndFlush(participation);
		} catch (DataIntegrityViolationException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof ConstraintViolationException violation
						&& "23505".equals(violation.getSQLState())
						&& "uk_participation_challenge_user".equals(violation.getConstraintName())) {
					throw new BusinessException(ParticipationErrorCode.ALREADY_APPLIED);
				}
			}
			throw exception;
		}
	}

	@Override
	public Optional<Participation> findById(Long id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<Participation> findByChallengeIdAndUserId(Long challengeId, Long userId) {
		return jpaRepository.findByChallengeIdAndUserId(challengeId, userId);
	}

	@Override
	public List<Participation> findAllByIds(Collection<Long> ids) {
		return jpaRepository.findAllById(ids);
	}
}

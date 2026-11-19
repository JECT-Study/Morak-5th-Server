package com.morak.feature.participation.infrastructure.persistence;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class ParticipationRepositoryAdapter implements ParticipationRepository {

	private static final String CHALLENGE_USER_INDEX = "uk_participation_challenge_user";

	private final JpaParticipationRepository repository;
	private final EntityManager entityManager;

	ParticipationRepositoryAdapter(JpaParticipationRepository repository, EntityManager entityManager) {
		this.repository = repository;
		this.entityManager = entityManager;
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
	public Optional<Participation> findById(long id) {
		return repository.findById(id);
	}

	/** 잠금 조회는 이미 읽어 둔 엔티티의 값을 갱신하지 않으므로, 잠근 직후 최신 값으로 다시 읽는다 (챌린지 어댑터와 같은 이유). */
	@Override
	public Optional<Participation> findByIdForUpdate(long id) {
		Optional<Participation> locked = repository.findByIdForUpdate(id);
		locked.ifPresent(entityManager::refresh);
		return locked;
	}

	@Override
	public List<Participation> findPendingByUserIdForUpdate(long userId) {
		return refreshed(repository.findPendingByUserIdForUpdate(userId));
	}

	@Override
	public List<Participation> findHosts(Collection<Long> challengeIds) {
		if (challengeIds.isEmpty()) {
			return List.of();
		}
		return repository.findByChallengeIdInAndRole(challengeIds, ParticipationRole.HOST);
	}

	@Override
	public List<Participation> findPendingByChallenge(long challengeId) {
		return repository.findPendingByChallenge(challengeId);
	}

	@Override
	public List<Participation> findPendingByChallengeForUpdate(long challengeId) {
		return refreshed(repository.findPendingByChallengeForUpdate(challengeId));
	}

	@Override
	public List<Participation> findDiscardableByChallenge(long challengeId) {
		return repository.findDiscardableByChallenge(challengeId);
	}

	@Override
	public List<Participation> findDiscardableByChallengeForUpdate(long challengeId) {
		return refreshed(repository.findDiscardableByChallengeForUpdate(challengeId));
	}

	@Override
	public boolean existsActiveMemberExceptHost(long challengeId) {
		return repository.existsActiveMemberExceptHost(challengeId);
	}

	@Override
	public List<Participation> findActiveByChallengeForUpdate(long challengeId) {
		return refreshed(repository.findActiveByChallengeForUpdate(challengeId));
	}

	/**
	 * 잠근 행의 최신 값으로 다시 읽는다. 잠그기 전에 읽어 둔 신청이 그 사이 다른 트랜잭션에서 처리됐어도 오래된 상태로
	 * 다시 처리하지 않게 한다. 호출 시점에는 이 목록의 엔티티에 아직 저장하지 않은 변경이 없다.
	 */
	private List<Participation> refreshed(List<Participation> locked) {
		locked.forEach(entityManager::refresh);
		return locked;
	}
}

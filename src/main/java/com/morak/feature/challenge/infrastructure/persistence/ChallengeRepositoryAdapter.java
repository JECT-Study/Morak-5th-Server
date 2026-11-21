package com.morak.feature.challenge.infrastructure.persistence;

import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.exception.InviteCodeConflictException;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class ChallengeRepositoryAdapter implements ChallengeRepository {

	private static final String INVITE_CODE_INDEX = "uk_challenge_invite_code";

	private final JpaChallengeRepository repository;
	private final EntityManager entityManager;

	ChallengeRepositoryAdapter(JpaChallengeRepository repository, EntityManager entityManager) {
		this.repository = repository;
		this.entityManager = entityManager;
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

	@Override
	public Optional<Challenge> findById(long id) {
		return repository.findById(id);
	}

	/**
	 * 잠금 조회는 이미 영속성 컨텍스트에 있던 엔티티의 값을 갱신하지 않는다. 같은 트랜잭션에서 잠그기 전에 읽어 둔 챌린지가
	 * 있으면 그 사이 다른 트랜잭션이 바꾼 참여 인원이 반영되지 않아 변경이 사라질 수 있으므로, 잠근 직후 다시 읽는다.
	 * 이미 잠긴 행이라 이 읽기는 항상 최신 값이다.
	 */
	@Override
	public Optional<Challenge> findByIdForUpdate(long id) {
		Optional<Challenge> locked = repository.findByIdForUpdate(id);
		locked.ifPresent(entityManager::refresh);
		return locked;
	}

	@Override
	public List<Long> findStartDueIds(LocalDate today, long afterId, int limit) {
		return repository.findStartDueIds(today, afterId, limit);
	}
}

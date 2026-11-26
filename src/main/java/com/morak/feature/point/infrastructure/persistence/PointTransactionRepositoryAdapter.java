package com.morak.feature.point.infrastructure.persistence;

import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.point.domain.entity.PointTransaction;
import com.morak.feature.point.domain.repository.PointTransactionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class PointTransactionRepositoryAdapter implements PointTransactionRepository {

	private final JpaPointTransactionRepository jpaRepository;

	public PointTransactionRepositoryAdapter(JpaPointTransactionRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public PointTransaction save(PointTransaction transaction) {
		return jpaRepository.save(transaction);
	}

	@Override
	public Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey) {
		return jpaRepository.findByIdempotencyKey(idempotencyKey);
	}

	@Override
	public long countGrants(Long accountId, PointGrantReason reason, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
		return jpaRepository.countGrants(accountId, reason, fromInclusive, toExclusive);
	}

	@Override
	public List<PointTransaction> findByAccountId(Long accountId, Long beforeId, int limit) {
		PageRequest page = PageRequest.of(0, limit);
		return beforeId == null
				? jpaRepository.findLatest(accountId, page)
				: jpaRepository.findBefore(accountId, beforeId, page);
	}
}

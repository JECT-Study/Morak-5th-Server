package com.morak.feature.point.infrastructure.persistence;

import com.morak.feature.point.domain.entity.PointTransaction;
import com.morak.feature.point.domain.repository.PointTransactionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PointTransactionRepositoryAdapter implements PointTransactionRepository {

	private final JpaPointTransactionRepository jpaRepository;
	private final JdbcClient jdbc;

	public PointTransactionRepositoryAdapter(JpaPointTransactionRepository jpaRepository, JdbcClient jdbc) {
		this.jpaRepository = jpaRepository;
		this.jdbc = jdbc;
	}

	@Override
	public PointTransaction save(PointTransaction transaction) {
		return jpaRepository.save(transaction);
	}

	/** PostgreSQL advisory lock. 첫 번째 인자로 포인트용 락을 다른 용도의 락과 구분한다. */
	@Override
	public void lockIdempotencyKey(String idempotencyKey) {
		jdbc.sql("SELECT 1 FROM pg_advisory_xact_lock(hashtext('point_transaction'), hashtext(:key))")
				.param("key", idempotencyKey)
				.query(Integer.class)
				.single();
	}

	@Override
	public Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey) {
		return jpaRepository.findByIdempotencyKey(idempotencyKey);
	}

	@Override
	public List<PointTransaction> findByAccountId(Long accountId, Long beforeId, int limit) {
		PageRequest page = PageRequest.of(0, limit);
		return beforeId == null
				? jpaRepository.findLatest(accountId, page)
				: jpaRepository.findBefore(accountId, beforeId, page);
	}
}

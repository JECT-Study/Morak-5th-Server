package com.morak.feature.point.domain.repository;

import com.morak.feature.point.domain.entity.PointTransaction;
import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository {

	PointTransaction save(PointTransaction transaction);

	/** 이미 처리한 거래인지 확인할 때 쓴다. idempotency_key는 유니크다. */
	Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey);

	/** 계정의 거래를 id 내림차순으로 limit개. beforeId가 있으면 그보다 작은 id만. */
	List<PointTransaction> findByAccountId(Long accountId, Long beforeId, int limit);
}

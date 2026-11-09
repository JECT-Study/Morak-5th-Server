package com.morak.feature.point.domain.repository;

import com.morak.feature.point.domain.entity.PointTransaction;
import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository {

	PointTransaction save(PointTransaction transaction);

	/**
	 * 같은 idempotencyKey를 처리하는 트랜잭션을 한 줄로 세운다. 락은 트랜잭션이 끝날 때 풀린다.
	 * 늦게 온 쪽은 앞선 트랜잭션이 끝난 뒤 진행하므로, 이어서 조회하면 앞선 거래가 보인다.
	 */
	void lockIdempotencyKey(String idempotencyKey);

	/** 이미 처리한 거래인지 확인할 때 쓴다. idempotency_key는 유니크다. */
	Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey);

	/** 계정의 거래를 id 내림차순으로 limit개. beforeId가 있으면 그보다 작은 id만. */
	List<PointTransaction> findByAccountId(Long accountId, Long beforeId, int limit);
}

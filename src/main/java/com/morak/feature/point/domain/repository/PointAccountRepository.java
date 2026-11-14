package com.morak.feature.point.domain.repository;

import com.morak.feature.point.domain.entity.PointAccount;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PointAccountRepository {

	Optional<PointAccount> findByUserId(Long userId);

	/**
	 * 유저의 계정 행을 잠그고 계정 ID를 돌려준다. 계정이 없으면 잔액 0으로 만든 뒤 잠근다 (UPSERT).
	 * 락은 트랜잭션이 끝날 때 풀리므로, 같은 유저의 잔액 변경은 한 번에 하나씩만 진행된다.
	 */
	Long lockForUpdate(Long userId, LocalDateTime nowUtc);

	/** 잔액을 amount만큼 늘리고 바뀐 잔액을 돌려준다. */
	long increase(Long accountId, long amount, LocalDateTime nowUtc);

	/**
	 * 잔액이 amount 이상일 때만 amount만큼 줄이고 바뀐 잔액을 돌려준다.
	 * 잔액이 부족하면 아무것도 바꾸지 않고 빈 값을 돌려준다.
	 */
	Optional<Long> decrease(Long accountId, long amount, LocalDateTime nowUtc);
}

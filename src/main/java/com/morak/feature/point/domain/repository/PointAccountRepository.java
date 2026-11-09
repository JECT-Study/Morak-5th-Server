package com.morak.feature.point.domain.repository;

import com.morak.feature.point.domain.entity.PointAccount;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PointAccountRepository {

	Optional<PointAccount> findByUserId(Long userId);

	/**
	 * 잔액을 amount만큼 늘리고 바뀐 잔액을 돌려준다. 계정이 없으면 amount를 잔액으로 새로 만든다.
	 * 엔티티를 읽고 고쳐 쓰지 않고 한 번의 UPDATE로 처리해, 동시에 들어와도 증가분이 사라지지 않는다.
	 */
	BalanceChange increase(Long userId, long amount, LocalDateTime nowUtc);

	/**
	 * 잔액이 amount 이상일 때만 amount만큼 줄이고 바뀐 잔액을 돌려준다.
	 * 잔액이 부족하거나 계정이 없으면 아무것도 바꾸지 않고 빈 값을 돌려준다.
	 */
	Optional<BalanceChange> decrease(Long userId, long amount, LocalDateTime nowUtc);

	record BalanceChange(Long accountId, long balanceAfter) {}
}

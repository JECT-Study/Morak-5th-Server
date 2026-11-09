package com.morak.feature.point.infrastructure.persistence;

import com.morak.feature.point.domain.entity.PointAccount;
import com.morak.feature.point.domain.repository.PointAccountRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 조회는 JPA로, 잔액 변경은 RETURNING으로 바뀐 잔액을 바로 받아야 해서 JDBC로 처리한다. */
@Repository
public class PointAccountRepositoryAdapter implements PointAccountRepository {

	private final JpaPointAccountRepository jpaRepository;
	private final JdbcClient jdbc;

	public PointAccountRepositoryAdapter(JpaPointAccountRepository jpaRepository, JdbcClient jdbc) {
		this.jpaRepository = jpaRepository;
		this.jdbc = jdbc;
	}

	@Override
	public Optional<PointAccount> findByUserId(Long userId) {
		return jpaRepository.findByUserId(userId);
	}

	@Override
	public BalanceChange increase(Long userId, long amount, LocalDateTime nowUtc) {
		return jdbc.sql("""
				INSERT INTO morak.point_account (user_id, balance, created_at, updated_at)
				VALUES (:userId, :amount, :now, :now)
				ON CONFLICT (user_id) DO UPDATE
				SET balance = point_account.balance + EXCLUDED.balance, updated_at = EXCLUDED.updated_at
				RETURNING id, balance
				""")
				.param("userId", userId)
				.param("amount", amount)
				.param("now", nowUtc)
				.query((rs, rowNum) -> new BalanceChange(rs.getLong("id"), rs.getLong("balance")))
				.single();
	}

	@Override
	public Optional<BalanceChange> decrease(Long userId, long amount, LocalDateTime nowUtc) {
		return jdbc.sql("""
				UPDATE morak.point_account
				SET balance = balance - :amount, updated_at = :now
				WHERE user_id = :userId AND balance >= :amount
				RETURNING id, balance
				""")
				.param("userId", userId)
				.param("amount", amount)
				.param("now", nowUtc)
				.query((rs, rowNum) -> new BalanceChange(rs.getLong("id"), rs.getLong("balance")))
				.optional();
	}
}

package com.morak.feature.point.infrastructure.persistence;

import com.morak.feature.point.domain.entity.PointAccount;
import com.morak.feature.point.domain.repository.PointAccountRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 조회는 JPA로, 락과 잔액 변경은 RETURNING으로 결과를 바로 받아야 해서 JDBC로 처리한다. */
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

	/** 동시에 처음 만드는 경우 늦은 쪽은 유니크 인덱스에서 기다렸다가 아무것도 하지 않고, 이어서 같은 행을 잠근다. */
	@Override
	public Long lockForUpdate(Long userId, LocalDateTime nowUtc) {
		jdbc.sql("""
				INSERT INTO morak.point_account (user_id, balance, created_at, updated_at)
				VALUES (:userId, 0, :now, :now)
				ON CONFLICT (user_id) DO NOTHING
				""")
				.param("userId", userId)
				.param("now", nowUtc)
				.update();
		return jdbc.sql("SELECT id FROM morak.point_account WHERE user_id = :userId FOR UPDATE")
				.param("userId", userId)
				.query(Long.class)
				.single();
	}

	@Override
	public long increase(Long accountId, long amount, LocalDateTime nowUtc) {
		return jdbc.sql("""
				UPDATE morak.point_account
				SET balance = balance + :amount, updated_at = :now
				WHERE id = :accountId
				RETURNING balance
				""")
				.param("accountId", accountId)
				.param("amount", amount)
				.param("now", nowUtc)
				.query(Long.class)
				.single();
	}

	@Override
	public Optional<Long> decrease(Long accountId, long amount, LocalDateTime nowUtc) {
		return jdbc.sql("""
				UPDATE morak.point_account
				SET balance = balance - :amount, updated_at = :now
				WHERE id = :accountId AND balance >= :amount
				RETURNING balance
				""")
				.param("accountId", accountId)
				.param("amount", amount)
				.param("now", nowUtc)
				.query(Long.class)
				.optional();
	}
}

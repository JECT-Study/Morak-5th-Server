package com.morak.feature.point.infrastructure.persistence;

import com.morak.feature.point.domain.entity.PointAccount;
import com.morak.feature.point.domain.repository.PointAccountRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

	/** ORDER BY 뒤에 행을 잠그므로(PostgreSQL의 LockRows는 정렬 결과 순서로 잠근다) 잠금 순서가 유저 ID 오름차순으로 고정된다. */
	@Override
	public void lockAllForUpdate(Collection<Long> userIds, LocalDateTime nowUtc) {
		if (userIds.isEmpty()) {
			return;
		}
		List<Long> ids = userIds.stream().distinct().sorted().toList();
		jdbc.sql("""
				INSERT INTO morak.point_account (user_id, balance, created_at, updated_at)
				SELECT user_id, 0, :now, :now FROM unnest(ARRAY[:userIds]::bigint[]) AS t(user_id) ORDER BY user_id
				ON CONFLICT (user_id) DO NOTHING
				""")
				.param("userIds", ids)
				.param("now", nowUtc)
				.update();
		List<Long> locked = jdbc.sql("""
				SELECT user_id FROM morak.point_account
				WHERE user_id IN (:userIds)
				ORDER BY user_id
				FOR UPDATE
				""")
				.param("userIds", ids)
				.query(Long.class)
				.list();
		if (locked.size() != ids.size()) {
			throw new IllegalStateException("잠그지 못한 포인트 계정이 있습니다. expected=" + ids.size() + " locked=" + locked.size());
		}
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

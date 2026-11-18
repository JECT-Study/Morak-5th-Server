package com.morak.feature.point.infrastructure.persistence;

import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.point.domain.entity.PointTransaction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPointTransactionRepository extends JpaRepository<PointTransaction, Long> {

	Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey);

	@Query("select count(t) from PointTransaction t where t.accountId = :accountId and t.type = 'GRANT' and t.reason = :reason"
			+ " and t.createdAt >= :from and t.createdAt < :to")
	long countGrants(
			@Param("accountId") Long accountId,
			@Param("reason") PointGrantReason reason,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to);

	@Query("select t from PointTransaction t where t.accountId = :accountId order by t.id desc")
	List<PointTransaction> findLatest(@Param("accountId") Long accountId, Pageable pageable);

	@Query("select t from PointTransaction t where t.accountId = :accountId and t.id < :beforeId order by t.id desc")
	List<PointTransaction> findBefore(
			@Param("accountId") Long accountId, @Param("beforeId") Long beforeId, Pageable pageable);
}

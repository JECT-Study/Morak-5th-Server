package com.morak.feature.point.infrastructure.persistence;

import com.morak.feature.point.domain.entity.PointAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPointAccountRepository extends JpaRepository<PointAccount, Long> {

	Optional<PointAccount> findByUserId(Long userId);
}

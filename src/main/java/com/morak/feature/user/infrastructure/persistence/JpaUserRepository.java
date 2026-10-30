package com.morak.feature.user.infrastructure.persistence;

import com.morak.feature.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserRepository extends JpaRepository<User, Long> {}

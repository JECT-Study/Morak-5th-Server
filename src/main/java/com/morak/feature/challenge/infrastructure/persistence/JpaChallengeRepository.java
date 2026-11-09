package com.morak.feature.challenge.infrastructure.persistence;

import com.morak.feature.challenge.domain.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaChallengeRepository extends JpaRepository<Challenge, Long> {}

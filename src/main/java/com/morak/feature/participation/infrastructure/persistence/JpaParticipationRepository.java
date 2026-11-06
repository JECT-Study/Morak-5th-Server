package com.morak.feature.participation.infrastructure.persistence;

import com.morak.feature.participation.domain.entity.Participation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaParticipationRepository extends JpaRepository<Participation, Long> {

	Optional<Participation> findByChallengeIdAndUserId(Long challengeId, Long userId);
}

package com.morak.feature.participation.domain.repository;

import com.morak.feature.participation.domain.entity.Participation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipationRepository {

	/** 같은 챌린지에 이미 참가 정보가 있으면 ALREADY_APPLIED 예외를 던진다. */
	Participation save(Participation participation);

	Optional<Participation> findById(Long id);

	/** 챌린지·유저당 한 행만 존재한다. */
	Optional<Participation> findByChallengeIdAndUserId(Long challengeId, Long userId);

	/** 존재하지 않는 id는 결과에 없다. */
	List<Participation> findAllByIds(Collection<Long> ids);
}

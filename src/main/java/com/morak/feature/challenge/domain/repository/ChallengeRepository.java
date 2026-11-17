package com.morak.feature.challenge.domain.repository;

import com.morak.feature.challenge.domain.entity.Challenge;
import java.util.Optional;

public interface ChallengeRepository {

	Challenge save(Challenge challenge);

	Optional<Challenge> findById(long id);

	/** 행 잠금으로 가져온다. 지원·승인처럼 참여 인원을 바꾸는 흐름에서 쓴다. */
	Optional<Challenge> findByIdForUpdate(long id);
}

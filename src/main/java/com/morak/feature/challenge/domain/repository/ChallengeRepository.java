package com.morak.feature.challenge.domain.repository;

import com.morak.feature.challenge.domain.entity.Challenge;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository {

	Challenge save(Challenge challenge);

	Optional<Challenge> findById(long id);

	/** 행 잠금으로 가져온다. 지원·승인처럼 참여 인원을 바꾸는 흐름에서 쓴다. */
	Optional<Challenge> findByIdForUpdate(long id);

	/**
	 * 시작 처리 대상 후보 id를 id 오름차순으로 반환한다(위키 §4.9 ②). 모집 중이고 시작일이 오늘 이전인 챌린지(누락 보정 포함)와,
	 * 시작 보류이고 보류한 날이 오늘보다 앞선 챌린지(보류된 당일 제외). 잠금 없이 읽으므로 처리할 때 다시 확인해야 한다.
	 */
	List<Long> findStartDueIds(LocalDate today, long afterId, int limit);
}

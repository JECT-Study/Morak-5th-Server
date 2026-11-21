package com.morak.feature.participation.domain.repository;

import com.morak.feature.participation.domain.entity.ParticipationResult;
import java.util.Collection;
import java.util.Set;

public interface ParticipationResultRepository {

	/** 참가자당 한 행만 허용한다. 이미 있는 참가자를 다시 저장하면 유니크 인덱스에 걸리므로 먼저 findExistingParticipationIds로 거른다. */
	ParticipationResult save(ParticipationResult result);

	/** 이미 결과가 있는 참가자 ID. 정산을 여러 번 돌려도 같은 결과가 되도록 저장 전에 거를 때 쓴다. */
	Set<Long> findExistingParticipationIds(Collection<Long> participationIds);
}

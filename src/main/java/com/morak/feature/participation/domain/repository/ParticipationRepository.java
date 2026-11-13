package com.morak.feature.participation.domain.repository;

import com.morak.feature.participation.domain.entity.Participation;
import java.util.Collection;
import java.util.List;

public interface ParticipationRepository {

	/** 같은 챌린지에 이미 참가 정보가 있으면 ALREADY_APPLIED 예외를 던진다. */
	Participation save(Participation participation);

	/** 사용자의 승인 대기 신청을 행 잠금으로 가져온다. 오래된 신청부터 정렬한다. */
	List<Participation> findPendingByUserIdForUpdate(long userId);

	/** 지정한 챌린지들의 방장 참가자. 방장이 없는 챌린지는 결과에 없다. */
	List<Participation> findHosts(Collection<Long> challengeIds);
}

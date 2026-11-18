package com.morak.feature.participation.domain.repository;

import com.morak.feature.participation.domain.entity.Participation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipationRepository {

	/** 같은 챌린지에 이미 참가 정보가 있으면 ALREADY_APPLIED 예외를 던진다. */
	Participation save(Participation participation);

	Optional<Participation> findById(long id);

	/** 행 잠금으로 가져온다. 승인·거절처럼 상태를 바꾸는 흐름에서 쓴다. */
	Optional<Participation> findByIdForUpdate(long id);

	/** 사용자의 승인 대기 신청을 행 잠금으로 가져온다. 오래된 신청부터 정렬한다. */
	List<Participation> findPendingByUserIdForUpdate(long userId);

	/** 지정한 챌린지들의 방장 참가자. 방장이 없는 챌린지는 결과에 없다. */
	List<Participation> findHosts(Collection<Long> challengeIds);

	/** 챌린지의 승인 대기(PENDING) 신청을 잠금 없이 읽는다. 락 순서 때문에 신청자의 포인트 계정을 먼저 잠글 때 쓴다. */
	List<Participation> findPendingByChallenge(long challengeId);

	/** 챌린지의 승인 대기(PENDING) 신청을 행 잠금으로 가져온다. 오래된 신청부터 정렬한다. */
	List<Participation> findPendingByChallengeForUpdate(long challengeId);

	/** 방장을 뺀 참여 중(ACTIVE) 참가자가 한 명이라도 있는지. 첫 합류자(부방장 지정 대상)인지 판단할 때 쓴다. */
	boolean existsActiveMemberExceptHost(long challengeId);

	/** 챌린지의 참여 중(ACTIVE) 참가자를 행 잠금으로 가져온다. 합류 순서(joined_at, id)로 정렬한다. */
	List<Participation> findActiveByChallengeForUpdate(long challengeId);
}

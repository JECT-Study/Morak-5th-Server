package com.morak.feature.participation.application.port.out;

import java.util.Optional;

/** 지원·승인이 챌린지 도메인에서 읽고 바꾸는 것들. */
public interface ApplicationChallenges {

	/** 잠금 없이 읽는다. 개설 후 바뀌지 않는 값을 락 순서 때문에 먼저 알아야 할 때 쓴다. */
	Optional<ChallengeInfo> find(long challengeId);

	/** 챌린지 행을 잠그고 읽는다. */
	Optional<ChallengeInfo> lock(long challengeId);

	/** 승인으로 챌린지 참여 인원을 1 늘린다. */
	void acceptMember(long challengeId);

	/** 동시 참여 한도 안에서 더 참여할 수 있는지. 위키 §4.3, O-17. */
	boolean canJoinMore(long userId);

	record ChallengeInfo(
			long challengeId,
			long ownerUserId,
			String title,
			boolean isPrivate,
			String inviteCode,
			boolean recruiting,
			boolean full,
			int pointAmount) {}
}

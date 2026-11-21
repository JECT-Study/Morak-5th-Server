package com.morak.feature.challenge.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 참가 도메인에서 개설 트랜잭션에 연결할 기능. */
public interface ChallengeParticipation {

	/** 개설자를 HOST·ACTIVE로 등록한다. 독립 트랜잭션으로 커밋하지 않는다. */
	long registerHost(long challengeId, long userId, Instant now, int pointAmount);

	/** 포인트 처리가 필요한 대기 신청이 하나라도 있으면 변경 없이 empty를 반환한다. */
	Optional<List<InvalidatedApplication>> invalidatePendingWithoutPoints(long userId);

	/** 폐기에 대비해 포인트가 걸린 참가자의 계정을 사용자 번호 순으로 잠그고, 잠근 사용자 번호를 돌려준다. 챌린지를 잠그기 전에 부른다. */
	Set<Long> lockPointAccountsForDiscard(long challengeId);

	/**
	 * 참가자(대기·참여 중) 전원을 폐기 상태로 바꾸고 걸린 포인트를 전액 돌려준다. 챌린지를 잠근 뒤에 부른다.
	 * 잠그지 못한 포인트 보유자(그 사이 새 신청)가 있으면 {@code CHALLENGE_DISCARD_APPLICATIONS_CHANGED}로 거절한다.
	 */
	List<DiscardedParticipant> discardAll(long challengeId, String challengeTitle, Set<Long> lockedUserIds, Instant now);

	/** 시작에 대비해 포인트가 걸린 승인 대기 신청자의 계정을 사용자 번호 순으로 잠그고, 잠근 사용자 번호를 돌려준다. 챌린지를 잠그기 전에 부른다. */
	Set<Long> lockPendingPointAccounts(long challengeId);

	/**
	 * 시작할 때 남은 승인 대기 신청을 모두 거절하고 걸린 포인트를 전액 돌려주며 알림 #2를 보낸다. 챌린지를 잠근 뒤에 부른다.
	 * 잠그지 못한 포인트 보유자(그 사이 새 신청)가 있으면 {@code CHALLENGE_START_APPLICATIONS_CHANGED}로 거절한다.
	 *
	 * @return 거절한 신청 수
	 */
	int rejectPendingOnStart(long challengeId, String challengeTitle, Set<Long> lockedUserIds);

	record DiscardedParticipant(long participationId, long userId, boolean host, int refundedPoint) {}

	record InvalidatedApplication(long id, long challengeId, long hostUserId) {}
}

package com.morak.feature.participation.application.command;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 다른 모듈(챌린지 등)에 공개하는 참가자 변경 API.
 *
 * <p>모두 호출한 쪽의 트랜잭션에 참여한다. 트랜잭션 없이 호출하면 예외가 난다.
 */
public interface ParticipationCommand {

	/** 개설자를 HOST·ACTIVE 참가자로 등록한다. 같은 챌린지에 이미 참가 정보가 있으면 예외. */
	void registerHost(long challengeId, long userId, Instant now);

	/**
	 * 사용자의 승인 대기 신청을 모두 무효(INVALIDATED)로 바꾼다. 포인트가 걸린 신청이 하나라도 있으면 반환 처리가 필요하므로
	 * 아무것도 바꾸지 않고 empty를 반환한다.
	 */
	Optional<List<InvalidatedApplication>> invalidatePendingWithoutPoints(long userId);

	/** 무효가 된 신청. {@code hostUserId}는 신청한 챌린지의 방장으로, 무효 알림을 받는다. */
	record InvalidatedApplication(long participationId, long challengeId, long hostUserId) {}
}

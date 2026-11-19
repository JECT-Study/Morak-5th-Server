package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.DiscardedParticipant;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작 보류 챌린지의 폐기 처리(위키 §4.2). 수동 폐기(방장)와 이후 자동 폐기 스케줄러(§4.9 ③)가 함께 쓴다.
 * 방장 확인만 호출한 쪽이 정하고(스케줄러는 {@code requiredOwnerUserId}에 null), 알림 종류(#6/#7)도 호출한 쪽이 고른다.
 *
 * <p>락 순서는 포인트 계정(사용자 번호 오름차순) → 챌린지 → 참가자다.
 * TODO(D-26): 폐기 API 경로·멱등 응답·알림 대상은 위키에 없는 제안이다.
 */
@Service
public class DiscardChallengeTransaction {

	private final ChallengeRepository challenges;
	private final Optional<ChallengeParticipation> participationPort;
	private final Clock clock;

	public DiscardChallengeTransaction(
			ChallengeRepository challenges, Optional<ChallengeParticipation> participationPort, Clock clock) {
		this.challenges = challenges;
		this.participationPort = participationPort;
		this.clock = clock;
	}

	/**
	 * 챌린지를 폐기하고 참가자 전원의 포인트를 돌려준다. 이미 폐기됐으면 아무것도 바꾸지 않고 {@link Result#alreadyDiscarded}를 돌려준다.
	 *
	 * @param requiredOwnerUserId 방장만 폐기할 수 있을 때 그 사용자 번호. 시스템 호출이면 null
	 */
	@Transactional
	public Result discard(long challengeId, Long requiredOwnerUserId) {
		ChallengeParticipation participants = participationPort
				.orElseThrow(() -> new BusinessException(ChallengeErrorCode.DISCARD_INTEGRATION_UNAVAILABLE));
		// 1. 잠금 없이 읽어 빨리 거절하고, 포인트 계정을 잠글지 정한다.
		Challenge unlocked = load(challenges.findById(challengeId), requiredOwnerUserId);
		if (unlocked.isDiscarded()) {
			return Result.alreadyDiscarded(challengeId);
		}
		unlocked.requireStartHeld();
		// 2. 포인트 계정 (사용자 번호 오름차순)
		Set<Long> lockedUserIds = unlocked.getPointAmount() > 0
				? participants.lockPointAccountsForDiscard(challengeId)
				: Set.of();
		// 3. 챌린지. 잠그는 사이 다른 요청이 처리했을 수 있으므로 다시 확인한다.
		Challenge challenge = load(challenges.findByIdForUpdate(challengeId), requiredOwnerUserId);
		if (challenge.isDiscarded()) {
			return Result.alreadyDiscarded(challengeId);
		}
		challenge.discard();
		// 4. 참가자 + 포인트 반환
		List<DiscardedParticipant> discarded =
				participants.discardAll(challengeId, challenge.getTitle(), lockedUserIds, clock.instant());
		return new Result(challengeId, challenge.getTitle(), false, discarded);
	}

	private static Challenge load(Optional<Challenge> found, Long requiredOwnerUserId) {
		Challenge challenge = found.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (requiredOwnerUserId != null && challenge.getOwnerUserId() != requiredOwnerUserId) {
			throw new BusinessException(ChallengeErrorCode.NOT_OWNER);
		}
		return challenge;
	}

	/** @param participants 이번 호출에서 폐기된 참가. 이미 폐기돼 있었으면 비어 있다 */
	public record Result(long challengeId, String title, boolean alreadyDiscarded, List<DiscardedParticipant> participants) {

		static Result alreadyDiscarded(long challengeId) {
			return new Result(challengeId, null, true, List.of());
		}
	}
}

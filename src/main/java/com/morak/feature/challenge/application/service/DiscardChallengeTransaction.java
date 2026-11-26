package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.DiscardedParticipant;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작 보류 챌린지의 폐기 처리(위키 §4.2). 수동 폐기(방장, {@link #discard})와 자동 폐기 스케줄러(§4.9 ③,
 * {@link #discardIfExpired})가 함께 쓴다. 포인트 계정 잠금부터 참가자 반환까지는 같은 코드이고, 진입 조건만 다르다.
 * 알림 종류(#6/#7)는 호출한 쪽이 고른다.
 *
 * <p>락 순서는 포인트 계정(사용자 번호 오름차순) → 챌린지 → 참가자다.
 * TODO(D-26): 폐기 API 경로·멱등 응답·알림 대상은 위키에 없는 제안이다.
 */
@Service
public class DiscardChallengeTransaction {

	private static final Logger log = LoggerFactory.getLogger(DiscardChallengeTransaction.class);

	private final ChallengeRepository challenges;
	private final Optional<ChallengeParticipation> participationPort;
	private final ChallengeParticipationReader participationReader;
	private final Clock clock;

	public DiscardChallengeTransaction(ChallengeRepository challenges, Optional<ChallengeParticipation> participationPort,
			ChallengeParticipationReader participationReader, Clock clock) {
		this.challenges = challenges;
		this.participationPort = participationPort;
		this.participationReader = participationReader;
		this.clock = clock;
	}

	/**
	 * 챌린지를 폐기하고 참가자 전원의 포인트를 돌려준다. 이미 폐기됐으면 아무것도 바꾸지 않고 {@link Outcome#ALREADY_DISCARDED}를 돌려준다.
	 * 시작 보류가 아니면 {@code CHALLENGE_NOT_START_HELD}로 거절한다.
	 *
	 * @param requiredOwnerUserId 방장만 폐기할 수 있을 때 그 사용자 번호. 시스템 호출이면 null
	 */
	@Transactional
	public Result discard(long challengeId, Long requiredOwnerUserId) {
		ChallengeParticipation participants = participants();
		// 1. 잠금 없이 읽어 빨리 거절하고, 포인트 계정을 잠글지 정한다.
		Challenge unlocked = load(challenges.findById(challengeId), requiredOwnerUserId);
		if (unlocked.isDiscarded()) {
			return Result.alreadyDiscarded(challengeId);
		}
		unlocked.requireStartHeld();
		// 2. 포인트 계정 (사용자 번호 오름차순)
		Set<Long> lockedUserIds = lockPointAccounts(participants, challengeId, unlocked);
		// 3. 챌린지. 잠그는 사이 다른 요청이 처리했을 수 있으므로 다시 확인한다.
		Challenge challenge = load(challenges.findByIdForUpdate(challengeId), requiredOwnerUserId);
		if (challenge.isDiscarded()) {
			return Result.alreadyDiscarded(challengeId);
		}
		// 4. 챌린지 상태 + 참가자 + 포인트 반환
		return discardLocked(participants, challengeId, challenge, lockedUserIds);
	}

	/**
	 * 자동 폐기(위키 §4.9 ③). 방장 확인 없이, 추가 모집 마감이 지난 시작 보류 챌린지만 폐기한다.
	 * 후보를 고른 뒤 방장이 재설정·폐기했거나 마감 전 인원이 차 있으면 예외 없이 {@link Outcome#SKIPPED}를 돌려준다.
	 *
	 * <p>TODO(D-30): 마감 전 합류 인원이 최소 인원 이상이면 폐기하지 않는다. 시작 처리(②)가 그 챌린지에서 실패했을 때
	 * 시작해야 할 챌린지를 폐기하지 않기 위한 안전장치이며, 다음 시작 처리가 시작시킨다(D-29 ④).
	 * ②가 계속 실패하면 시작도 폐기도 되지 않으므로 error 로그로 알린다.
	 */
	@Transactional
	public Result discardIfExpired(long challengeId, LocalDate today) {
		// 1. 잠금 없이 읽어 대상이 아니면 빨리 끝낸다.
		Optional<Challenge> unlocked = challenges.findById(challengeId);
		if (unlocked.isEmpty() || !unlocked.get().isDiscardDue(today)) {
			return Result.skipped(challengeId);
		}
		ChallengeParticipation participants = participants();
		// 2. 포인트 계정 (사용자 번호 오름차순)
		Set<Long> lockedUserIds = lockPointAccounts(participants, challengeId, unlocked.get());
		// 3. 챌린지. 잠그는 사이 방장이 재설정·폐기했을 수 있으므로 다시 확인한다.
		Optional<Challenge> found = challenges.findByIdForUpdate(challengeId);
		if (found.isEmpty()) {
			return Result.skipped(challengeId);
		}
		Challenge challenge = found.get();
		if (challenge.isDiscarded()) {
			return Result.alreadyDiscarded(challengeId);
		}
		if (!challenge.isDiscardDue(today)) {
			log.info("자동 폐기 건너뜀(상태 변경): challengeId={}, status={}", challengeId, challenge.getStatus());
			return Result.skipped(challengeId);
		}
		int joined = participationReader.countJoinedBefore(challengeId, challenge.recruitDeadlineEnd());
		if (challenge.hasEnoughMembers(joined)) {
			// 정상이면 ②가 먼저 시작시켜 여기 오지 않는다. 왔다면 ②가 이 챌린지에서 실패한 것이라 운영자가 봐야 한다.
			log.error("자동 폐기 건너뜀(마감 전 인원 충족, 시작 처리 실패 의심): challengeId={}, joined={}, today={}", challengeId,
					joined, today);
			return Result.skipped(challengeId);
		}
		// 4. 챌린지 상태 + 참가자 + 포인트 반환
		return discardLocked(participants, challengeId, challenge, lockedUserIds);
	}

	private Result discardLocked(
			ChallengeParticipation participants, long challengeId, Challenge challenge, Set<Long> lockedUserIds) {
		challenge.discard();
		List<DiscardedParticipant> discarded =
				participants.discardAll(challengeId, challenge.getTitle(), lockedUserIds, clock.instant());
		return new Result(challengeId, challenge.getTitle(), Outcome.DISCARDED, discarded);
	}

	private ChallengeParticipation participants() {
		return participationPort
				.orElseThrow(() -> new BusinessException(ChallengeErrorCode.DISCARD_INTEGRATION_UNAVAILABLE));
	}

	private static Set<Long> lockPointAccounts(ChallengeParticipation participants, long challengeId, Challenge challenge) {
		return challenge.getPointAmount() > 0 ? participants.lockPointAccountsForDiscard(challengeId) : Set.of();
	}

	private static Challenge load(Optional<Challenge> found, Long requiredOwnerUserId) {
		Challenge challenge = found.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (requiredOwnerUserId != null && challenge.getOwnerUserId() != requiredOwnerUserId) {
			throw new BusinessException(ChallengeErrorCode.NOT_OWNER);
		}
		return challenge;
	}

	public enum Outcome {
		/** 이번 호출에서 폐기했다. */
		DISCARDED,
		/** 이미 폐기돼 있어 아무것도 바꾸지 않았다. */
		ALREADY_DISCARDED,
		/** 자동 폐기 대상이 아니어서 아무것도 바꾸지 않았다. */
		SKIPPED
	}

	/** @param participants 이번 호출에서 폐기된 참가. 폐기하지 않았으면 비어 있다 */
	public record Result(long challengeId, String title, Outcome outcome, List<DiscardedParticipant> participants) {

		static Result alreadyDiscarded(long challengeId) {
			return new Result(challengeId, null, Outcome.ALREADY_DISCARDED, List.of());
		}

		static Result skipped(long challengeId) {
			return new Result(challengeId, null, Outcome.SKIPPED, List.of());
		}
	}
}

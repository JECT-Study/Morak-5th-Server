package com.morak.feature.participation.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.application.port.out.ApplicantPoints;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationChallenges.ChallengeInfo;
import com.morak.feature.participation.application.port.out.ApplicationNotifier;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방장의 신청 승인·거절과 무효 신청 확인(위키 §4.3). 처리 결과는 신청자에게 인앱 알림(#1 승인, #2 거절)으로 남긴다.
 * 승인할 때 개설 후 처음 합류한 참여자는 부방장이 되고(#8), 승인으로 정원이 차면 남은 대기 신청은 모두 자동 거절한다(#2).
 * 락 순서는 포인트 계정 → 챌린지 → 참가자이므로, 포인트가 걸린 신청은 챌린지를 잠그기 전에 지원자 계정부터 잠근다.
 *
 * <p>TODO(O-17): 승인으로 신청자의 동시 참여가 5개가 되면 그 사람의 다른 대기 신청을 무효화한다.
 * <p>TODO(D-24): 자동 거절 중 신청 목록이 바뀐 경우의 409 처리와 무효 확인 API는 위키에 없는 제안이다.
 */
@Service
public class DecideApplicationService {

	/** 이 승인으로 정원이 찰 수 있는 남은 자리 수. 이 값 이하일 때만 자동 거절에 대비해 포인트 계정을 미리 잠근다. */
	private static final int FILLING_REMAINING_SLOTS = 1;

	private final ApplicationChallenges challenges;
	private final ApplicantPoints points;
	private final ApplicationNotifier notifier;
	private final ParticipationRepository participations;
	private final Clock clock;

	public DecideApplicationService(
			ApplicationChallenges challenges,
			ApplicantPoints points,
			ApplicationNotifier notifier,
			ParticipationRepository participations,
			Clock clock) {
		this.challenges = challenges;
		this.points = points;
		this.notifier = notifier;
		this.participations = participations;
		this.clock = clock;
	}

	/**
	 * 신청을 승인해 참여 중(ACTIVE)으로 만들고 참여 인원을 늘린다.
	 * 개설 후 처음 합류한 참여자는 부방장(DEPUTY)이 되고, 승인으로 정원이 차면 남은 대기 신청을 모두 거절하고 포인트를 돌려준다.
	 */
	@Transactional
	public void approve(long hostUserId, long challengeId, long participationId) {
		// 정원이 찰 수 있는 승인이면, 자동 거절로 포인트를 돌려줄 대기자들의 계정을 챌린지보다 먼저 잠근다(락 순서).
		Set<Long> lockedAccounts = challenges.find(challengeId)
				.filter(challenge -> challenge.remainingSlots() <= FILLING_REMAINING_SLOTS)
				.map(challenge -> lockPendingApplicantAccounts(challengeId, participationId))
				.orElse(Set.of());
		ChallengeInfo challenge = lockChallengeOwnedBy(hostUserId, challengeId);
		Participation application = lockApplication(challengeId, participationId);
		// 처음 합류한 참여자인지는 이 신청이 ACTIVE가 되기 전에 확인한다.
		boolean firstMember = !participations.existsActiveMemberExceptHost(challengeId);
		// 상태 전이 위반(무효·거절된 신청 등)을 가장 먼저 걸러낸다. 이후 검사에서 실패해도 트랜잭션째 롤백된다.
		application.approve(clock.instant());
		if (!challenge.recruiting()) {
			throw new BusinessException(ParticipationErrorCode.CHALLENGE_NOT_RECRUITING);
		}
		if (challenge.full()) {
			throw new BusinessException(ParticipationErrorCode.CHALLENGE_FULL);
		}
		challenges.acceptMember(challengeId);
		notifier.approved(application.getUserId(), challengeId, participationId, challenge.title());
		if (firstMember) {
			application.assignDeputy();
			notifier.deputyAssigned(application.getUserId(), challengeId, participationId, challenge.title());
		}
		if (challenge.remainingSlots() == FILLING_REMAINING_SLOTS) {
			rejectRemainingApplications(challenge, participationId, lockedAccounts);
		}
	}

	/** 신청을 거절한다. 걸어 둔 포인트는 전액 돌려준다. */
	@Transactional
	public void reject(long hostUserId, long challengeId, long participationId) {
		Participation unlocked = participations.findById(participationId)
				.filter(p -> p.getChallengeId() == challengeId)
				.orElseThrow(() -> new BusinessException(ParticipationErrorCode.APPLICATION_NOT_FOUND));
		if (unlocked.isPointHeld()) {
			points.lockAccount(unlocked.getUserId());
		}
		ChallengeInfo challenge = lockChallengeOwnedBy(hostUserId, challengeId);
		Participation application = lockApplication(challengeId, participationId);
		boolean pointHeld = application.isPointHeld();
		application.reject();
		int refunded = 0;
		if (pointHeld) {
			refunded = application.getPointAmount();
			points.release(application.getUserId(), participationId, challenge.title(), refunded);
		}
		notifier.rejected(application.getUserId(), challengeId, participationId, challenge.title(), refunded);
	}

	/**
	 * 동시 참여 한도 때문에 무효가 된 신청을 방장이 확인한다(알림 #3의 "확인" 버튼). 확인하면 승인 대기 목록에서 사라진다.
	 * 이미 확인한 신청이면 그대로 성공한다. 무효가 아닌 신청은 409다.
	 */
	@Transactional
	public void confirmInvalidation(long hostUserId, long challengeId, long participationId) {
		lockChallengeOwnedBy(hostUserId, challengeId);
		lockApplication(challengeId, participationId).confirmInvalidation(clock.instant());
	}

	/** 지원자 중 포인트가 걸린 신청자의 계정을 사용자 번호 순서로 잠그고, 잠근 사용자 번호를 돌려준다. */
	private Set<Long> lockPendingApplicantAccounts(long challengeId, long approvingParticipationId) {
		Set<Long> userIds = new TreeSet<>();
		for (Participation pending : participations.findPendingByChallenge(challengeId)) {
			if (pending.getId() != approvingParticipationId && pending.isPointHeld()) {
				userIds.add(pending.getUserId());
			}
		}
		userIds.forEach(points::lockAccount);
		return userIds;
	}

	/**
	 * 정원이 찬 챌린지의 남은 대기 신청을 모두 거절하고 포인트를 돌려준다(알림 #2).
	 * 계정을 미리 잠그지 못한 신청자가 있으면(그 사이에 새로 지원한 경우) 락 순서를 지킬 수 없어 전체를 되돌리고 다시 시도하게 한다.
	 */
	private void rejectRemainingApplications(ChallengeInfo challenge, long approvedParticipationId, Set<Long> lockedAccounts) {
		List<Participation> remaining = participations.findPendingByChallengeForUpdate(challenge.challengeId()).stream()
				.filter(pending -> pending.getId() != approvedParticipationId)
				.toList();
		for (Participation pending : remaining) {
			if (pending.isPointHeld() && !lockedAccounts.contains(pending.getUserId())) {
				throw new BusinessException(ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED);
			}
		}
		for (Participation pending : remaining) {
			boolean pointHeld = pending.isPointHeld();
			int refunded = pointHeld ? pending.getPointAmount() : 0;
			pending.reject();
			if (pointHeld) {
				points.release(pending.getUserId(), pending.getId(), challenge.title(), refunded);
			}
			notifier.rejected(pending.getUserId(), challenge.challengeId(), pending.getId(), challenge.title(), refunded);
		}
	}

	private ChallengeInfo lockChallengeOwnedBy(long hostUserId, long challengeId) {
		ChallengeInfo challenge = challenges.lock(challengeId)
				.orElseThrow(() -> new BusinessException(ParticipationErrorCode.APPLICATION_NOT_FOUND));
		if (challenge.ownerUserId() != hostUserId) {
			throw new BusinessException(ParticipationErrorCode.NOT_CHALLENGE_HOST);
		}
		return challenge;
	}

	private Participation lockApplication(long challengeId, long participationId) {
		return participations.findByIdForUpdate(participationId)
				.filter(p -> p.getChallengeId() == challengeId)
				.orElseThrow(() -> new BusinessException(ParticipationErrorCode.APPLICATION_NOT_FOUND));
	}
}

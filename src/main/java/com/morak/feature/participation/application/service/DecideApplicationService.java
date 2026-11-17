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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방장의 신청 승인·거절(위키 §4.3). 처리 결과는 신청자에게 인앱 알림(#1 승인, #2 거절)으로 남긴다.
 * 락 순서는 포인트 계정 → 챌린지 → 참가자이므로, 포인트가 걸린 신청은 챌린지를 잠그기 전에 지원자 계정부터 잠근다.
 *
 * <p>TODO(O-17): 승인으로 신청자의 동시 참여가 5개가 되면 그 사람의 다른 대기 신청을 무효화한다.
 * <p>TODO: 정원이 차는 순간 남은 대기 신청 자동 거절(#2)과 첫 합류자 부방장 지정(#8)은 아직 구현하지 않았다.
 */
@Service
public class DecideApplicationService {

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

	/** 신청을 승인해 참여 중(ACTIVE)으로 만들고 참여 인원을 늘린다. */
	@Transactional
	public void approve(long hostUserId, long challengeId, long participationId) {
		ChallengeInfo challenge = lockChallengeOwnedBy(hostUserId, challengeId);
		Participation application = lockApplication(challengeId, participationId);
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

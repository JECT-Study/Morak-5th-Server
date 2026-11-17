package com.morak.feature.participation.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.port.out.ApplicantPoints;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationChallenges.ChallengeInfo;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챌린지 참가 지원(위키 §4.3). 포인트가 있는 챌린지는 지원과 동시에 포인트를 건다.
 * 정원이 다 찬 챌린지(모집 마감)에는 지원할 수 없다. 정원이 남아 있는 동안은 승인 대기 인원이 정원을 넘을 수 있으므로
 * 승인 시점에 정원을 한 번 더 확인한다.
 * 락 순서는 포인트 계정 → 챌린지 → 참가자다.
 */
@Service
public class ApplyParticipationService {

	private final ApplicationChallenges challenges;
	private final ApplicantPoints points;
	private final ParticipationRepository participations;
	private final Clock clock;

	public ApplyParticipationService(
			ApplicationChallenges challenges,
			ApplicantPoints points,
			ParticipationRepository participations,
			Clock clock) {
		this.challenges = challenges;
		this.points = points;
		this.participations = participations;
		this.clock = clock;
	}

	/** 지원서를 내고 승인 대기(PENDING) 참가자를 만든다. 반환값은 참가자 ID다. */
	@Transactional
	public long apply(long userId, long challengeId, String applyMessage, String inviteCode) {
		// 포인트는 개설 후 바뀌지 않으므로, 락 순서를 지키려고 챌린지를 잠그기 전에 읽어 계정부터 잠근다.
		int pointAmount = challenges.find(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
				.pointAmount();
		if (pointAmount > 0) {
			points.lockAccount(userId);
		}
		ChallengeInfo challenge = challenges.lock(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (challenge.isPrivate() && !challenge.inviteCode().equals(inviteCode)) {
			throw new BusinessException(ParticipationErrorCode.INVALID_INVITE_CODE);
		}
		if (!challenge.recruiting()) {
			throw new BusinessException(ParticipationErrorCode.CHALLENGE_NOT_RECRUITING);
		}
		// 정원이 다 차면 모집 마감이라 지원할 수 없다(위키 §4.2, §4.3). 정원 안에서는 승인 대기 인원이 정원을 넘을 수 있다.
		if (challenge.full()) {
			throw new BusinessException(ParticipationErrorCode.CHALLENGE_FULL);
		}
		if (!challenges.canJoinMore(userId)) {
			throw new BusinessException(ParticipationErrorCode.PARTICIPATION_LIMIT_EXCEEDED);
		}
		Instant now = clock.instant();
		Participation participation = participations.save(
				Participation.apply(challengeId, userId, applyMessage, challenge.pointAmount(), now));
		if (challenge.pointAmount() > 0) {
			points.hold(userId, participation.getId(), challenge.title(), challenge.pointAmount());
		}
		return participation.getId();
	}
}

package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.application.port.out.ChallengeOwnerLock;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import com.morak.feature.notification.application.InvalidatedApplicationNotice;
import com.morak.feature.participation.application.ChallengeParticipation;
import com.morak.feature.participation.application.ChallengeParticipation.InvalidatedApplication;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateChallengeTransaction {

	private static final int PARTICIPATION_LIMIT = 5;

	private final ChallengeRepository challenges;
	private final ChallengeParticipation participants;
	private final ChallengeOwnerLock owners;
	private final InvalidatedApplicationNotice notices;
	private final Clock clock;

	public CreateChallengeTransaction(ChallengeRepository challenges, ChallengeParticipation participants,
			ChallengeOwnerLock owners, InvalidatedApplicationNotice notices, Clock clock) {
		this.challenges = challenges;
		this.participants = participants;
		this.owners = owners;
		this.notices = notices;
		this.clock = clock;
	}

	/** 사용자 잠금으로 개설을 직렬화하고 방장 등록과 대기 신청 무효화를 함께 저장한다. */
	@Transactional
	public long create(long ownerId, CreateChallengeCommand command, String inviteCode) {
		if (command.pointAmount() != 0) {
			throw new BusinessException(ChallengeErrorCode.POINTS_NOT_SUPPORTED);
		}
		owners.lock(ownerId);
		long ongoing = participants.countOngoing(ownerId);
		if (ongoing >= PARTICIPATION_LIMIT) {
			throw new BusinessException(ChallengeErrorCode.PARTICIPATION_LIMIT);
		}
		Instant now = clock.instant();
		command.validateStartDate(now);
		long challengeId = challenges.save(command.toChallenge(ownerId, inviteCode, now)).getId();
		List<InvalidatedApplication> invalidated = ongoing + 1 == PARTICIPATION_LIMIT
				? participants.invalidatePendingWithoutPoints(ownerId)
						.orElseThrow(() -> new BusinessException(ChallengeErrorCode.PENDING_POINTS_NOT_SUPPORTED))
				: List.of();
		participants.registerHost(challengeId, ownerId, now);
		for (InvalidatedApplication application : invalidated) {
			notices.send(application.hostUserId(), application.challengeId(), application.id(), now);
		}
		return challengeId;
	}
}

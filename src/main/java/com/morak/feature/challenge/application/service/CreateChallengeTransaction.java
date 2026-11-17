package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.command.CreateChallengeCommand;
import com.morak.feature.challenge.application.port.out.ChallengeOwnerLock;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.InvalidatedApplication;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.port.out.InvalidatedApplicationNotice;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import com.morak.feature.point.application.service.PointService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateChallengeTransaction {

	private static final int PARTICIPATION_LIMIT = 5;
	// HOTFIX: 동시 참여 5개 제한을 임시로 해제했다(위키 §9 참고). 다시 설정하려면 true로 되돌린다.
	private static final boolean PARTICIPATION_LIMIT_ENABLED = false;

	private final ChallengeRepository challenges;
	private final ChallengeParticipationReader participationReader;
	private final Optional<ChallengeParticipation> participationPort;
	private final ChallengeOwnerLock owners;
	private final Optional<InvalidatedApplicationNotice> noticePort;
	private final Clock clock;
	private final PointService points;

	public CreateChallengeTransaction(ChallengeRepository challenges, ChallengeParticipationReader participationReader,
			Optional<ChallengeParticipation> participationPort, ChallengeOwnerLock owners,
			Optional<InvalidatedApplicationNotice> noticePort, Clock clock, PointService points) {
		this.challenges = challenges;
		this.participationReader = participationReader;
		this.participationPort = participationPort;
		this.owners = owners;
		this.noticePort = noticePort;
		this.clock = clock;
		this.points = points;
	}

	/** 사용자 잠금으로 개설을 직렬화하고 방장 등록과 대기 신청 무효화를 함께 저장한다. */
	@Transactional
	public long create(long ownerId, CreateChallengeCommand command, String inviteCode) {
		return create(ownerId, command, inviteCode, null);
	}

	@Transactional
	public long create(long ownerId, CreateChallengeCommand command, String inviteCode, String coverImageKey) {
		ChallengeParticipation participants = participationPort
				.orElseThrow(() -> new BusinessException(ChallengeErrorCode.CREATION_INTEGRATION_UNAVAILABLE));
		owners.lock(ownerId);
		int ongoing = participationReader.countOngoing(ownerId);
		if (PARTICIPATION_LIMIT_ENABLED && ongoing >= PARTICIPATION_LIMIT) {
			throw new BusinessException(ChallengeErrorCode.PARTICIPATION_LIMIT);
		}
		Instant now = clock.instant();
		command.validateStartDate(now);
		if (command.pointAmount() > 0) {
			points.lockAccountForChallengeCreation(ownerId);
		}
		long challengeId = challenges.save(command.toChallenge(ownerId, inviteCode, coverImageKey)).getId();
		List<InvalidatedApplication> invalidated = PARTICIPATION_LIMIT_ENABLED && ongoing + 1 == PARTICIPATION_LIMIT
				? participants.invalidatePendingWithoutPoints(ownerId)
						.orElseThrow(() -> new BusinessException(ChallengeErrorCode.PENDING_POINTS_NOT_SUPPORTED))
				: List.of();
		long participationId = participants.registerHost(challengeId, ownerId, now, command.pointAmount());
		if (command.pointAmount() > 0) {
			if (points == null) {
				throw new BusinessException(ChallengeErrorCode.CREATION_INTEGRATION_UNAVAILABLE);
			}
			points.deposit(ownerId, participationId, command.title(), command.pointAmount());
		}
		if (!invalidated.isEmpty()) {
			InvalidatedApplicationNotice notices = noticePort
					.orElseThrow(() -> new BusinessException(ChallengeErrorCode.CREATION_INTEGRATION_UNAVAILABLE));
			for (InvalidatedApplication application : invalidated) {
				notices.send(application.hostUserId(), application.challengeId(), application.id(), now);
			}
		}
		return challengeId;
	}
}

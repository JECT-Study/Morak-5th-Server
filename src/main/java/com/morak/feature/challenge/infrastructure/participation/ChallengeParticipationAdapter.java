package com.morak.feature.challenge.infrastructure.participation;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class ChallengeParticipationAdapter implements ChallengeParticipation {

	private final ParticipationCommand participationCommand;

	ChallengeParticipationAdapter(ParticipationCommand participationCommand) {
		this.participationCommand = participationCommand;
	}

	@Override
	public long registerHost(long challengeId, long userId, Instant now, int pointAmount) {
		return participationCommand.registerHost(challengeId, userId, now, pointAmount);
	}

	@Override
	public Optional<List<InvalidatedApplication>> invalidatePendingWithoutPoints(long userId) {
		return participationCommand.invalidatePendingWithoutPoints(userId)
				.map(applications -> applications.stream()
						.map(a -> new InvalidatedApplication(a.participationId(), a.challengeId(), a.hostUserId()))
						.toList());
	}

	@Override
	public Set<Long> lockPointAccountsForDiscard(long challengeId) {
		return participationCommand.lockPointAccountsForDiscard(challengeId);
	}

	@Override
	public List<DiscardedParticipant> discardAll(
			long challengeId, String challengeTitle, Set<Long> lockedUserIds, Instant now) {
		try {
			return participationCommand.discardAll(challengeId, challengeTitle, lockedUserIds, now).stream()
					.map(d -> new DiscardedParticipant(d.participationId(), d.userId(), d.host(), d.refundedPoint()))
					.toList();
		} catch (BusinessException exception) {
			if (exception.getErrorCode() == ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED) {
				// 챌린지 쪽은 참가 도메인 오류 코드를 모르게, 재시도할 수 있는 폐기 오류로 바꿔 넘긴다.
				throw new BusinessException(ChallengeErrorCode.DISCARD_APPLICATIONS_CHANGED);
			}
			throw exception;
		}
	}
}

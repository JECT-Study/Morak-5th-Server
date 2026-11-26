package com.morak.feature.participation.infrastructure.challenge;

import com.morak.feature.challenge.application.command.ChallengeCommand;
import com.morak.feature.challenge.application.command.ChallengeCommand.ChallengeForApplication;
import com.morak.feature.challenge.domain.policy.ParticipationLimitPolicy;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.query.ParticipationQuery;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ChallengeApplicationChallenges implements ApplicationChallenges {

	private final ChallengeCommand challengeCommand;
	private final ParticipationQuery participationQuery;

	ChallengeApplicationChallenges(ChallengeCommand challengeCommand, ParticipationQuery participationQuery) {
		this.challengeCommand = challengeCommand;
		this.participationQuery = participationQuery;
	}

	@Override
	public Optional<ChallengeInfo> find(long challengeId) {
		return challengeCommand.find(challengeId).map(ChallengeApplicationChallenges::toInfo);
	}

	@Override
	public Optional<ChallengeInfo> lock(long challengeId) {
		return challengeCommand.lockForUpdate(challengeId).map(ChallengeApplicationChallenges::toInfo);
	}

	@Override
	public void acceptMember(long challengeId) {
		challengeCommand.acceptMember(challengeId);
	}

	@Override
	public void changeOwner(long challengeId, long newOwnerUserId) {
		challengeCommand.changeOwner(challengeId, newOwnerUserId);
	}

	@Override
	public boolean canJoinMore(long userId) {
		return ParticipationLimitPolicy.canJoinMore(() -> participationQuery.countOngoing(userId));
	}

	private static ChallengeInfo toInfo(ChallengeForApplication challenge) {
		return new ChallengeInfo(
				challenge.challengeId(),
				challenge.ownerUserId(),
				challenge.title(),
				"PRIVATE".equals(challenge.visibility()),
				challenge.inviteCode(),
				challenge.recruiting(),
				challenge.isFull(),
				challenge.pointAmount(),
				challenge.capacity() - challenge.currentCount());
	}
}

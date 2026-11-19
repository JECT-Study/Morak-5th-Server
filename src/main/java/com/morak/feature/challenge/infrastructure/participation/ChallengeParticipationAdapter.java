package com.morak.feature.challenge.infrastructure.participation;

import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.participation.application.command.ParticipationCommand;
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
		return participationCommand.discardAll(challengeId, challengeTitle, lockedUserIds, now).stream()
				.map(d -> new DiscardedParticipant(d.participationId(), d.userId(), d.host(), d.refundedPoint()))
				.toList();
	}
}

package com.morak.feature.challenge.infrastructure.participation;

import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.participation.application.command.ParticipationCommand;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ChallengeParticipationAdapter implements ChallengeParticipation {

	private final ParticipationCommand participationCommand;

	ChallengeParticipationAdapter(ParticipationCommand participationCommand) {
		this.participationCommand = participationCommand;
	}

	@Override
	public void registerHost(long challengeId, long userId, Instant now) {
		participationCommand.registerHost(challengeId, userId, now);
	}

	@Override
	public Optional<List<InvalidatedApplication>> invalidatePendingWithoutPoints(long userId) {
		return participationCommand.invalidatePendingWithoutPoints(userId)
				.map(applications -> applications.stream()
						.map(a -> new InvalidatedApplication(a.participationId(), a.challengeId(), a.hostUserId()))
						.toList());
	}
}

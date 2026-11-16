package com.morak.feature.challenge.infrastructure.participation;

import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.participation.application.query.ParticipationQuery;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ChallengeParticipationReaderAdapter implements ChallengeParticipationReader {

	private final ParticipationQuery participationQuery;

	ChallengeParticipationReaderAdapter(ParticipationQuery participationQuery) {
		this.participationQuery = participationQuery;
	}

	@Override
	public int countOngoing(long userId) {
		return participationQuery.countOngoing(userId);
	}

	@Override
	public Optional<String> findStatus(long challengeId, long userId) {
		return participationQuery.findMyParticipation(challengeId, userId)
				.map(ParticipationQuery.MyParticipation::status);
	}
}

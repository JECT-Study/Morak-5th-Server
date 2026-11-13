package com.morak.feature.challenge.infrastructure.participation;

import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.participation.application.query.ParticipationQuery;
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
}

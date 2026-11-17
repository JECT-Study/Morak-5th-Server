package com.morak.feature.user.infrastructure.participation;

import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.user.application.port.out.OngoingParticipationReader;
import org.springframework.stereotype.Component;

@Component
class OngoingParticipationReaderAdapter implements OngoingParticipationReader {

	private final ParticipationQuery participationQuery;

	OngoingParticipationReaderAdapter(ParticipationQuery participationQuery) {
		this.participationQuery = participationQuery;
	}

	@Override
	public int countOngoing(long userId) {
		return participationQuery.countOngoing(userId);
	}
}

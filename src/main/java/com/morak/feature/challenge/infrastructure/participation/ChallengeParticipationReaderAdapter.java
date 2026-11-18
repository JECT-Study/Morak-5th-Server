package com.morak.feature.challenge.infrastructure.participation;

import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.MyParticipation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

	@Override
	public List<MyParticipationRow> findMine(long userId, Set<String> statuses) {
		return participationQuery.findMyParticipations(userId, statuses).stream().map(this::toRow).toList();
	}

	@Override
	public List<MyParticipationRow> findMine(long userId, Set<String> statuses, Long cursor, int limit) {
		return participationQuery.findMyParticipations(userId, statuses, cursor, limit).stream().map(this::toRow).toList();
	}

	private MyParticipationRow toRow(MyParticipation participation) {
		return new MyParticipationRow(participation.participationId(), participation.challengeId(), participation.role(),
				participation.status());
	}
}

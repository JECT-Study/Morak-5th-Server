package com.morak.feature.participation.infrastructure.persistence;

import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import com.morak.feature.participation.domain.vo.ParticipationStatus;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 다른 모듈에 공개하는 참가자 조회 API를 참가자 리포지토리로 구현한다. */
@Component
public class ParticipationQueryAdapter implements ParticipationQuery {

	private static final Set<ParticipationStatus> JOINED_STATUSES = Set.of(
			ParticipationStatus.ACTIVE, ParticipationStatus.EXPEL_NOTICED, ParticipationStatus.COMPLETED);

	private final ParticipationRepository repository;

	public ParticipationQueryAdapter(ParticipationRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<ParticipantInfo> findJoinedParticipant(Long challengeId, Long userId) {
		return repository.findByChallengeIdAndUserId(challengeId, userId)
				.filter(participation -> JOINED_STATUSES.contains(participation.getStatus()))
				.map(ParticipationQueryAdapter::toInfo);
	}

	@Override
	public Map<Long, ParticipantInfo> findParticipantsByIds(Collection<Long> participationIds) {
		Map<Long, ParticipantInfo> result = new HashMap<>();
		if (participationIds.isEmpty()) {
			return result;
		}
		for (Participation participation : repository.findAllByIds(participationIds)) {
			result.put(participation.getId(), toInfo(participation));
		}
		return result;
	}

	private static ParticipantInfo toInfo(Participation participation) {
		return new ParticipantInfo(participation.getId(), participation.getUserId(),
				participation.getRole().name(), participation.getStatus().name());
	}
}

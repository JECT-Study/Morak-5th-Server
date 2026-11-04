package com.morak.feature.participation.application.query;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 다른 모듈(채팅 등)에 공개하는 참가자 조회 API. */
public interface ParticipationQuery {

	/** 챌린지에 참여 중(ACTIVE)이거나 완주(COMPLETED)한 참가자. 없으면 empty. */
	Optional<ParticipantInfo> findJoinedParticipant(Long challengeId, Long userId);

	/** participationId -> userId. */
	Map<Long, Long> findUserIdsByParticipationIds(Collection<Long> participationIds);

	record ParticipantInfo(Long participationId, Long userId, String role, String status) {

		public boolean isActive() {
			return "ACTIVE".equals(status);
		}
	}
}

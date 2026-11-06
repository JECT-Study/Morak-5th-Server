package com.morak.feature.participation.application.query;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 다른 모듈(채팅 등)에 공개하는 참가자 조회 API. */
public interface ParticipationQuery {

	/** 챌린지에 참여 중(ACTIVE)이거나 퇴장 공지를 받았거나(EXPEL_NOTICED) 완주(COMPLETED)한 참가자. 없으면 empty. */
	Optional<ParticipantInfo> findJoinedParticipant(Long challengeId, Long userId);

	/** participationId -> 참가자. 존재하지 않는 id는 결과에 없다. */
	Map<Long, ParticipantInfo> findParticipantsByIds(Collection<Long> participationIds);

	record ParticipantInfo(Long participationId, Long userId, String role, String status) {

		public boolean isActive() {
			return "ACTIVE".equals(status);
		}
	}
}

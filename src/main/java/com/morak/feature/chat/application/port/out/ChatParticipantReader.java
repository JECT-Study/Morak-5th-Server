package com.morak.feature.chat.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 참가자 도메인에서 채팅에 필요한 정보만 읽어오는 포트. */
public interface ChatParticipantReader {

	/** 채팅방 이용 대상(ACTIVE, COMPLETED) 참가자만 반환한다. 없으면 empty. */
	Optional<ChatParticipant> findChatParticipant(Long challengeId, Long userId);

	/** participationId -> userId. */
	Map<Long, Long> findUserIdsByParticipationIds(Collection<Long> participationIds);

	Optional<LocalDate> findChallengeEndDate(Long challengeId);

	record ChatParticipant(Long participationId, Long userId, String role, String status) {

		public boolean isActive() {
			return "ACTIVE".equals(status);
		}
	}
}

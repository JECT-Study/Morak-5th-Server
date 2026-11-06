package com.morak.feature.chat.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 참가자 도메인에서 채팅에 필요한 정보만 읽어오는 포트. */
public interface ChatParticipantReader {

	/**
	 * 채팅방 이용 대상(ACTIVE, EXPEL_NOTICED, COMPLETED) 참가자만 반환한다. 없으면 empty.
	 * 이 중 전송까지 가능한 것은 ACTIVE뿐이다.
	 */
	Optional<ChatParticipant> findChatParticipant(Long challengeId, Long userId);

	/** participationId -> 보낸 사람 표시 정보. 찾을 수 없는 participationId는 결과에 없다. */
	Map<Long, ChatSender> findSenders(Collection<Long> participationIds);

	Optional<LocalDate> findChallengeEndDate(Long challengeId);

	record ChatParticipant(Long participationId, Long userId, String role, String status) {

		public boolean isActive() {
			return "ACTIVE".equals(status);
		}
	}

	record ChatSender(Long userId, String nickname, String profileImageUrl, String role) {}
}

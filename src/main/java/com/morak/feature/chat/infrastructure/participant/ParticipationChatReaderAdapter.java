package com.morak.feature.chat.infrastructure.participant;

import com.morak.feature.challenge.application.query.ChallengeQuery;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.participation.application.query.ParticipationQuery;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 채팅 포트를 참가자/챌린지 모듈의 공개 조회 API로 연결한다. */
@Component
public class ParticipationChatReaderAdapter implements ChatParticipantReader {

	private final ParticipationQuery participationQuery;
	private final ChallengeQuery challengeQuery;

	public ParticipationChatReaderAdapter(ParticipationQuery participationQuery, ChallengeQuery challengeQuery) {
		this.participationQuery = participationQuery;
		this.challengeQuery = challengeQuery;
	}

	@Override
	public Optional<ChatParticipant> findChatParticipant(Long challengeId, Long userId) {
		return participationQuery.findJoinedParticipant(challengeId, userId)
				.map(info -> new ChatParticipant(info.participationId(), info.userId(), info.role(), info.status()));
	}

	@Override
	public Map<Long, Long> findUserIdsByParticipationIds(Collection<Long> participationIds) {
		return participationQuery.findUserIdsByParticipationIds(participationIds);
	}

	@Override
	public Optional<LocalDate> findChallengeEndDate(Long challengeId) {
		return challengeQuery.findEndDate(challengeId);
	}
}

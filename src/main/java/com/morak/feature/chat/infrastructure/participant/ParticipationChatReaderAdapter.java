package com.morak.feature.chat.infrastructure.participant;

import com.morak.feature.challenge.application.query.ChallengeQuery;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipantInfo;
import com.morak.feature.user.application.query.UserQuery;
import com.morak.feature.user.application.query.UserQuery.UserProfile;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 채팅 포트를 참가자/사용자/챌린지 모듈의 공개 조회 API로 연결한다. */
@Component
public class ParticipationChatReaderAdapter implements ChatParticipantReader {

	private final ParticipationQuery participationQuery;
	private final UserQuery userQuery;
	private final ChallengeQuery challengeQuery;

	public ParticipationChatReaderAdapter(
			ParticipationQuery participationQuery, UserQuery userQuery, ChallengeQuery challengeQuery) {
		this.participationQuery = participationQuery;
		this.userQuery = userQuery;
		this.challengeQuery = challengeQuery;
	}

	@Override
	public Optional<ChatParticipant> findChatParticipant(Long challengeId, Long userId) {
		return participationQuery.findJoinedParticipant(challengeId, userId)
				.map(info -> new ChatParticipant(info.participationId(), info.userId(), info.role(), info.status()));
	}

	@Override
	public Map<Long, ChatSender> findSenders(Collection<Long> participationIds) {
		Map<Long, ParticipantInfo> participants = participationQuery.findParticipantsByIds(participationIds);
		Set<Long> userIds = participants.values().stream().map(ParticipantInfo::userId).collect(Collectors.toSet());
		Map<Long, UserProfile> profiles = userQuery.findProfilesByIds(userIds);

		Map<Long, ChatSender> result = new HashMap<>();
		participants.forEach((participationId, info) -> {
			UserProfile profile = profiles.get(info.userId());
			result.put(participationId, new ChatSender(
					info.userId(),
					profile == null ? null : profile.nickname(),
					profile == null ? null : profile.profileImageUrl(),
					info.role()));
		});
		return result;
	}

	@Override
	public Optional<LocalDate> findChallengeEndDate(Long challengeId) {
		return challengeQuery.findEndDate(challengeId);
	}
}

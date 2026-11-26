package com.morak.feature.certification.infrastructure.target;

import com.morak.feature.certification.application.port.out.CertificationFeedTargets;
import com.morak.feature.challenge.application.query.ChallengeQuery;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipantInfo;
import com.morak.feature.user.application.query.UserQuery;
import com.morak.feature.user.application.query.UserQuery.UserProfile;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 인증 피드 포트를 챌린지·참가·유저 모듈의 공개 조회 API로 연결한다. */
@Component
class ChallengeParticipationFeedTargets implements CertificationFeedTargets {

	private final ChallengeQuery challengeQuery;
	private final ParticipationQuery participationQuery;
	private final UserQuery userQuery;

	ChallengeParticipationFeedTargets(ChallengeQuery challengeQuery, ParticipationQuery participationQuery,
			UserQuery userQuery) {
		this.challengeQuery = challengeQuery;
		this.participationQuery = participationQuery;
		this.userQuery = userQuery;
	}

	@Override
	public boolean challengeExists(long challengeId) {
		return challengeQuery.findCertificationWindow(challengeId).isPresent();
	}

	@Override
	public Optional<Viewer> findViewer(long challengeId, long userId) {
		return participationQuery.findMembers(challengeId).stream()
				.filter(member -> member.userId() == userId)
				.findFirst()
				.map(member -> new Viewer(member.participationId(), member.status()));
	}

	@Override
	public Map<Long, Submitter> findSubmitters(Collection<Long> participationIds) {
		Map<Long, ParticipantInfo> participants = participationQuery.findParticipantsByIds(participationIds);
		Map<Long, UserProfile> profiles = userQuery.findProfilesByIds(
				participants.values().stream().map(ParticipantInfo::userId).collect(Collectors.toSet()));
		Map<Long, Submitter> result = new HashMap<>();
		participants.forEach((participationId, info) -> {
			UserProfile profile = profiles.get(info.userId());
			result.put(participationId, new Submitter(info.userId(), info.role(),
					profile == null ? null : profile.nickname(), profile == null ? null : profile.profileImageUrl()));
		});
		return result;
	}
}

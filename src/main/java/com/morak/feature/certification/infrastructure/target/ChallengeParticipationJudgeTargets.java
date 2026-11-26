package com.morak.feature.certification.infrastructure.target;

import com.morak.feature.certification.application.port.out.CertificationJudgeTargets;
import com.morak.feature.challenge.application.query.ChallengeQuery;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.user.application.query.UserQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 판정 포트를 챌린지·참가·유저 모듈의 공개 조회 API로 연결한다. */
@Component
class ChallengeParticipationJudgeTargets implements CertificationJudgeTargets {

	private final ChallengeQuery challengeQuery;
	private final ParticipationQuery participationQuery;
	private final UserQuery userQuery;

	ChallengeParticipationJudgeTargets(ChallengeQuery challengeQuery, ParticipationQuery participationQuery,
			UserQuery userQuery) {
		this.challengeQuery = challengeQuery;
		this.participationQuery = participationQuery;
		this.userQuery = userQuery;
	}

	@Override
	public Optional<Challenge> findChallenge(long challengeId) {
		return challengeQuery.findCertificationWindow(challengeId).map(window -> new Challenge(
				challengeQuery.findTitle(challengeId).orElse(""), window.startDate(), window.endDate(),
				window.pointAmount()));
	}

	@Override
	public List<Member> findMembers(long challengeId) {
		return participationQuery.findMembers(challengeId).stream()
				.map(m -> new Member(m.participationId(), m.userId(), m.role(), m.status()))
				.toList();
	}

	@Override
	public Map<Long, Member> findParticipants(Collection<Long> participationIds) {
		Map<Long, Member> result = new HashMap<>();
		participationQuery.findParticipantsByIds(participationIds).forEach((id, info) ->
				result.put(id, new Member(id, info.userId(), info.role(), info.status())));
		return result;
	}

	@Override
	public Map<Long, Profile> findProfiles(Collection<Long> userIds) {
		Map<Long, Profile> result = new HashMap<>();
		userQuery.findProfilesByIds(userIds).forEach((id, profile) ->
				result.put(id, new Profile(profile.nickname(), profile.profileImageUrl())));
		return result;
	}
}

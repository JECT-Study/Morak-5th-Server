package com.morak.feature.certification.infrastructure.target;

import com.morak.feature.certification.application.port.out.CertificationSubmitTargets;
import com.morak.feature.challenge.application.query.ChallengeQuery;
import com.morak.feature.participation.application.query.ParticipationQuery;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 인증 제출 포트를 챌린지·참가 모듈의 공개 조회 API로 연결한다. */
@Component
class ChallengeParticipationSubmitTargets implements CertificationSubmitTargets {

	private final ChallengeQuery challengeQuery;
	private final ParticipationQuery participationQuery;

	ChallengeParticipationSubmitTargets(ChallengeQuery challengeQuery, ParticipationQuery participationQuery) {
		this.challengeQuery = challengeQuery;
		this.participationQuery = participationQuery;
	}

	@Override
	public Optional<Challenge> findChallenge(long challengeId) {
		return challengeQuery.findCertificationWindow(challengeId).map(window -> new Challenge(
				"IN_PROGRESS".equals(window.status()), window.startDate(), window.endDate(), window.pointAmount()));
	}

	@Override
	public Optional<Participant> findParticipant(long challengeId, long userId) {
		return participationQuery.findJoinedParticipant(challengeId, userId)
				.map(info -> new Participant(info.participationId(), info.isActive()));
	}
}

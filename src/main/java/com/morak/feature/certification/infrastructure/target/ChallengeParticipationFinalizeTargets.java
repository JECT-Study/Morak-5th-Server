package com.morak.feature.certification.infrastructure.target;

import com.morak.feature.certification.application.port.out.CertificationFinalizeTargets;
import com.morak.feature.challenge.application.command.ChallengeCommand;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.application.command.ParticipationCommand.ResultInput;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** 결과 확정 포트를 챌린지·참가 모듈의 공개 변경 API로 연결한다. */
@Component
class ChallengeParticipationFinalizeTargets implements CertificationFinalizeTargets {

	private final ChallengeCommand challengeCommand;
	private final ParticipationCommand participationCommand;

	ChallengeParticipationFinalizeTargets(ChallengeCommand challengeCommand,
			ParticipationCommand participationCommand) {
		this.challengeCommand = challengeCommand;
		this.participationCommand = participationCommand;
	}

	@Override
	public void lockForFinalizing(long challengeId, LocalDate today) {
		challengeCommand.lockForFinalizing(challengeId, today);
	}

	@Override
	public void completeActive(long challengeId, Instant now) {
		participationCommand.completeActive(challengeId, now);
	}

	@Override
	public void recordResults(List<Result> results, Instant finalizedAt) {
		participationCommand.recordResults(results.stream()
				.map(r -> new ResultInput(r.participationId(), r.approvedCount(), r.totalCount(), r.currentStreak(),
						r.maxStreak(), r.completed()))
				.toList(), finalizedAt);
	}

	@Override
	public void end(long challengeId) {
		challengeCommand.end(challengeId);
	}
}

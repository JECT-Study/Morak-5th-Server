package com.morak.feature.certification.infrastructure.target;

import com.morak.feature.certification.application.port.out.CertificationExpelTargets;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.application.command.ParticipationCommand.ExpelOutcome;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.stereotype.Component;

/** 퇴장 포트를 참가 모듈의 공개 변경 API로 연결한다. */
@Component
class ParticipationExpelTargets implements CertificationExpelTargets {

	private final ParticipationCommand participationCommand;

	ParticipationExpelTargets(ParticipationCommand participationCommand) {
		this.participationCommand = participationCommand;
	}

	@Override
	public Outcome expel(long challengeId, Collection<Long> participationIds, LocalDate today, Instant now) {
		ExpelOutcome outcome = participationCommand.expel(challengeId, participationIds, today, now);
		return new Outcome(
				outcome.expelled().stream().map(m -> new Expelled(m.participationId(), m.userId())).toList(),
				outcome.earlyEndedUserIds());
	}
}

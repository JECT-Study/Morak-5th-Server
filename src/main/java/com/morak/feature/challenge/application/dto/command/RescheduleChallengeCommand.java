package com.morak.feature.challenge.application.dto.command;

import com.morak.feature.challenge.domain.policy.ChallengePeriod;
import java.time.LocalDate;

/** 시작 보류 챌린지의 새 기간. 저장 범위와 최소 7일은 만들 때 확인한다. */
public record RescheduleChallengeCommand(LocalDate startDate, LocalDate endDate) {

	public RescheduleChallengeCommand {
		ChallengePeriod.validate(startDate, endDate);
	}
}

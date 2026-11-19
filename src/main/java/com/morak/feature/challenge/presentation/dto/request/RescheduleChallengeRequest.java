package com.morak.feature.challenge.presentation.dto.request;

import com.morak.feature.challenge.application.dto.command.RescheduleChallengeCommand;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RescheduleChallengeRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {

	public RescheduleChallengeCommand toCommand() {
		return new RescheduleChallengeCommand(startDate, endDate);
	}
}

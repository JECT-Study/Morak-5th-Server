package com.morak.feature.challenge.infrastructure.participation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.feature.challenge.application.port.out.ChallengeParticipation.InvalidatedApplication;
import com.morak.feature.participation.application.command.ParticipationCommand;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChallengeParticipationAdapterTest {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");

	private final ParticipationCommand command = mock(ParticipationCommand.class);
	private final ChallengeParticipationAdapter adapter = new ChallengeParticipationAdapter(command);

	@Test
	void delegatesHostRegistration() {
		when(command.registerHost(10L, 1L, NOW, 0)).thenReturn(99L);
		assertEquals(99L, adapter.registerHost(10L, 1L, NOW, 0));

		verify(command).registerHost(10L, 1L, NOW, 0);
	}

	@Test
	void mapsInvalidatedApplicationsToChallengePort() {
		when(command.invalidatePendingWithoutPoints(7L)).thenReturn(Optional
				.of(List.of(new ParticipationCommand.InvalidatedApplication(100L, 20L, 3L))));

		assertEquals(List.of(new InvalidatedApplication(100L, 20L, 3L)),
				adapter.invalidatePendingWithoutPoints(7L).orElseThrow());
	}

	@Test
	void keepsEmptyWhenPointsAreInvolved() {
		when(command.invalidatePendingWithoutPoints(7L)).thenReturn(Optional.empty());

		assertTrue(adapter.invalidatePendingWithoutPoints(7L).isEmpty());
	}
}

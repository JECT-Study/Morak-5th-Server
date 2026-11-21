package com.morak.feature.challenge.infrastructure.participation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader.WaitingParticipant;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipantSummary;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChallengeParticipationReaderAdapterTest {

	private final ParticipationQuery query = mock(ParticipationQuery.class);
	private final ChallengeParticipationReaderAdapter adapter = new ChallengeParticipationReaderAdapter(query);

	@Test
	void waitingParticipantsAreActiveAndPendingWithoutHost() {
		Instant now = Instant.parse("2026-11-18T00:00:00Z");
		when(query.findParticipants(10L, Set.of("ACTIVE", "PENDING"))).thenReturn(List.of(
				new ParticipantSummary(100L, 1L, "HOST", "ACTIVE", now),
				new ParticipantSummary(101L, 2L, "DEPUTY", "ACTIVE", now),
				new ParticipantSummary(102L, 3L, "MEMBER", "PENDING", null)));

		List<WaitingParticipant> result = adapter.findWaitingParticipants(10L);

		assertEquals(List.of(new WaitingParticipant(101, 2), new WaitingParticipant(102, 3)), result);
	}

	@Test
	void countJoinedBeforeCountsOnlyMembersJoinedBeforeCutoff() {
		Instant cutoff = Instant.parse("2026-11-03T15:00:00Z");
		when(query.findParticipants(10L, Set.of("ACTIVE", "EXPEL_NOTICED"))).thenReturn(List.of(
				new ParticipantSummary(100L, 1L, "HOST", "ACTIVE", cutoff.minusSeconds(86_400)),
				new ParticipantSummary(101L, 2L, "DEPUTY", "ACTIVE", cutoff.minusNanos(1_000)),
				new ParticipantSummary(102L, 3L, "MEMBER", "ACTIVE", cutoff),
				new ParticipantSummary(103L, 4L, "MEMBER", "ACTIVE", null)));

		assertEquals(2, adapter.countJoinedBefore(10L, cutoff));
	}
}

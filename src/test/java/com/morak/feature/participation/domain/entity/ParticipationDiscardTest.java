package com.morak.feature.participation.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParticipationDiscardTest {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");

	@Test
	void pendingApplicationWithPointsIsDiscardedAndRefunded() {
		Participation application = Participation.apply(10L, 2L, "각오", 1_000, NOW);

		application.discard(NOW);

		assertEquals(ParticipationStatus.DISCARDED, application.getStatus());
		assertFalse(application.isPointHeld());
		assertTrue(application.hasPoints());
		assertEquals(Participation.END_REASON_DISCARDED, application.getEndReason());
		assertNotNull(application.getEndedAt());
	}

	@Test
	void activeHostWithPointsIsDiscardedAndRefunded() {
		Participation host = Participation.host(10L, 1L, NOW, 1_000);

		host.discard(NOW);

		assertEquals(ParticipationStatus.DISCARDED, host.getStatus());
		assertFalse(host.isPointHeld());
	}

	@Test
	void participationWithoutPointsIsDiscardedWithoutPointChange() {
		Participation member = Participation.apply(10L, 2L, "각오", NOW);

		member.discard(NOW);

		assertEquals(ParticipationStatus.DISCARDED, member.getStatus());
		assertFalse(member.hasPoints());
	}

	@Test
	void rejectedOrAlreadyDiscardedParticipationCannotBeDiscarded() {
		Participation rejected = Participation.apply(10L, 2L, "각오", 1_000, NOW);
		rejected.reject();
		Participation discarded = Participation.apply(10L, 3L, "각오", NOW);
		discarded.discard(NOW);

		for (Participation target : new Participation[] {rejected, discarded}) {
			BusinessException error = assertThrows(BusinessException.class, () -> target.discard(NOW));
			assertEquals(ParticipationErrorCode.INVALID_STATUS_TRANSITION, error.getErrorCode());
		}
		assertEquals(ParticipationStatus.REJECTED, rejected.getStatus());
	}
}

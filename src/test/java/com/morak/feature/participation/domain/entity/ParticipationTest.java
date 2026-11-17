package com.morak.feature.participation.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParticipationTest {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");

	@Test
	void hostStartsAsActiveHostWithoutPoints() {
		Participation host = Participation.host(10L, 1L, NOW);

		assertEquals(ParticipationRole.HOST, host.getRole());
		assertEquals(ParticipationStatus.ACTIVE, host.getStatus());
		assertEquals(10L, host.getChallengeId());
		assertEquals(1L, host.getUserId());
		assertFalse(host.hasPoints());
	}

	@Test
	void applyCreatesPendingMember() {
		Participation application = Participation.apply(10L, 2L, "열심히 하겠습니다", NOW);

		assertEquals(ParticipationRole.MEMBER, application.getRole());
		assertEquals(ParticipationStatus.PENDING, application.getStatus());
		assertFalse(application.hasPoints());
	}

	@Test
	void pendingApplicationCanBeInvalidated() {
		Participation application = Participation.apply(10L, 2L, "각오", NOW);

		application.invalidate();

		assertEquals(ParticipationStatus.INVALIDATED, application.getStatus());
	}

	@Test
	void invalidatingNonPendingParticipationIsRejected() {
		Participation host = Participation.host(10L, 1L, NOW);

		BusinessException error = assertThrows(BusinessException.class, host::invalidate);

		assertEquals(ParticipationErrorCode.INVALID_STATUS_TRANSITION, error.getErrorCode());
		assertEquals(ParticipationStatus.ACTIVE, host.getStatus());
	}

	@Test
	void applyWithPointsHoldsThem() {
		Participation application = Participation.apply(10L, 2L, "각오", 1_000, NOW);

		assertTrue(application.isPointHeld());
		assertEquals(1_000, application.getPointAmount());
		assertEquals(ParticipationStatus.PENDING, application.getStatus());
	}

	@Test
	void approvingPendingApplicationActivatesIt() {
		Participation application = Participation.apply(10L, 2L, "각오", NOW);

		application.approve(NOW);

		assertEquals(ParticipationStatus.ACTIVE, application.getStatus());
	}

	@Test
	void rejectingPendingApplicationRefundsHeldPoints() {
		Participation application = Participation.apply(10L, 2L, "각오", 1_000, NOW);

		application.reject();

		assertEquals(ParticipationStatus.REJECTED, application.getStatus());
		assertFalse(application.isPointHeld());
		assertTrue(application.hasPoints());
	}

	@Test
	void approvingOrRejectingNonPendingApplicationIsRejected() {
		Participation invalidated = Participation.apply(10L, 2L, "각오", NOW);
		invalidated.invalidate();
		Participation rejected = Participation.apply(10L, 3L, "각오", NOW);
		rejected.reject();

		assertThrows(BusinessException.class, () -> invalidated.approve(NOW));
		assertThrows(BusinessException.class, invalidated::reject);
		assertThrows(BusinessException.class, () -> rejected.approve(NOW));
		assertEquals(ParticipationStatus.INVALIDATED, invalidated.getStatus());
		assertEquals(ParticipationStatus.REJECTED, rejected.getStatus());
	}

	@Test
	void invalidatedApplicationCannotBeInvalidatedAgain() {
		Participation application = Participation.apply(10L, 2L, "각오", NOW);
		application.invalidate();

		assertThrows(BusinessException.class, application::invalidate);
	}
}

package com.morak.feature.participation.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	void invalidatedApplicationCannotBeInvalidatedAgain() {
		Participation application = Participation.apply(10L, 2L, "각오", NOW);
		application.invalidate();

		assertThrows(BusinessException.class, application::invalidate);
	}
}

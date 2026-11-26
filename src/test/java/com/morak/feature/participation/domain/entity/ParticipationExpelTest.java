package com.morak.feature.participation.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 위키 §4.3 참가 상태: ACTIVE → EXPELLED / EARLY_ENDED / COMPLETED. */
class ParticipationExpelTest {

	private static final Instant NOW = Instant.parse("2026-11-24T03:05:00Z");

	@Test
	void activeParticipantIsExpelledForFailLimit() {
		Participation participant = Participation.host(10L, 1L, NOW);

		participant.expel(NOW);

		assertEquals(ParticipationStatus.EXPELLED, participant.getStatus());
		assertEquals(Participation.END_REASON_FAIL_LIMIT, participant.getEndReason());
		assertNotNull(participant.getEndedAt());
	}

	@Test
	void activeParticipantEndsEarly() {
		Participation participant = Participation.host(10L, 1L, NOW);

		participant.endEarly(NOW);

		assertEquals(ParticipationStatus.EARLY_ENDED, participant.getStatus());
		assertEquals(Participation.END_REASON_EARLY_END, participant.getEndReason());
	}

	@Test
	void activeParticipantCompletesAndOnlyActiveCan() {
		Participation participant = Participation.host(10L, 1L, NOW);
		Participation earlyEnded = Participation.host(11L, 2L, NOW);
		earlyEnded.endEarly(NOW);

		participant.complete(NOW);

		assertEquals(ParticipationStatus.COMPLETED, participant.getStatus());
		assertEquals(Participation.END_REASON_COMPLETED, participant.getEndReason());
		assertNotNull(participant.getEndedAt());
		assertEquals(ParticipationErrorCode.INVALID_STATUS_TRANSITION,
				assertThrows(BusinessException.class, () -> earlyEnded.complete(NOW)).getErrorCode());
	}

	@Test
	void onlyActiveParticipantCanBeExpelledOrEndEarly() {
		Participation expelled = Participation.host(10L, 1L, NOW);
		expelled.expel(NOW);
		Participation pending = Participation.apply(10L, 2L, "각오", NOW);

		assertEquals(ParticipationErrorCode.INVALID_STATUS_TRANSITION,
				assertThrows(BusinessException.class, () -> expelled.expel(NOW)).getErrorCode());
		assertEquals(ParticipationErrorCode.INVALID_STATUS_TRANSITION,
				assertThrows(BusinessException.class, () -> pending.endEarly(NOW)).getErrorCode());
	}
}

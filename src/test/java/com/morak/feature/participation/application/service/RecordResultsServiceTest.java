package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.application.command.ParticipationCommand.ResultInput;
import com.morak.feature.participation.application.port.out.ApplicantPoints;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.entity.ParticipationResult;
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import com.morak.feature.participation.domain.repository.ParticipationResultRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RecordResultsServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

	private final ParticipationRepository participations = mock(ParticipationRepository.class);
	private final ParticipationResultRepository results = mock(ParticipationResultRepository.class);
	private final ParticipationCommandService service = new ParticipationCommandService(
			participations, results, mock(ApplicationChallenges.class), mock(ApplicantPoints.class),
			mock(com.morak.feature.participation.application.port.out.ApplicationNotifier.class));

	@Test
	void savesResultForActiveParticipantWithChallengeAndUserFromTheRow() {
		givenParticipants(participant(1L, 10L, 100L, ParticipationStatus.ACTIVE));
		when(results.findExistingParticipationIds(any())).thenReturn(Set.of());

		int saved = service.recordResults(List.of(new ResultInput(1L, 20, 25, 3, 7, true)), NOW);

		assertEquals(1, saved);
		ArgumentCaptor<ParticipationResult> captor = ArgumentCaptor.forClass(ParticipationResult.class);
		verify(results).save(captor.capture());
		ParticipationResult result = captor.getValue();
		assertEquals(10L, result.getChallengeId());
		assertEquals(100L, result.getUserId());
		assertEquals(new BigDecimal("80.00"), result.getAchievementRate());
	}

	@Test
	void skipsParticipantsThatAlreadyHaveAResult() {
		givenParticipants(participant(1L, 10L, 100L, ParticipationStatus.ACTIVE));
		when(results.findExistingParticipationIds(any())).thenReturn(Set.of(1L));

		int saved = service.recordResults(List.of(new ResultInput(1L, 20, 25, 3, 7, true)), NOW);

		assertEquals(0, saved);
		verify(results, never()).save(any());
	}

	@Test
	void savesOnlyOnceWhenTheSameParticipantIsListedTwice() {
		givenParticipants(participant(1L, 10L, 100L, ParticipationStatus.EXPELLED));
		when(results.findExistingParticipationIds(any())).thenReturn(Set.of());
		ResultInput input = new ResultInput(1L, 5, 10, 0, 2, false);

		int saved = service.recordResults(List.of(input, input), NOW);

		assertEquals(1, saved);
		verify(results, times(1)).save(any());
	}

	@Test
	void doesNotCreateRowsForDiscardedOrNeverApprovedParticipants() {
		givenParticipants(
				participant(1L, 10L, 100L, ParticipationStatus.DISCARDED),
				participant(2L, 10L, 101L, ParticipationStatus.PENDING),
				participant(3L, 10L, 102L, ParticipationStatus.REJECTED),
				participant(4L, 10L, 103L, ParticipationStatus.INVALIDATED));
		when(results.findExistingParticipationIds(any())).thenReturn(Set.of());

		int saved = service.recordResults(List.of(
				new ResultInput(1L, 0, 0, 0, 0, false),
				new ResultInput(2L, 0, 0, 0, 0, false),
				new ResultInput(3L, 0, 0, 0, 0, false),
				new ResultInput(4L, 0, 0, 0, 0, false)), NOW);

		assertEquals(0, saved);
		verify(results, never()).save(any());
	}

	@Test
	void savesForEveryStatusThatWasEverApproved() {
		givenParticipants(
				participant(1L, 10L, 100L, ParticipationStatus.ACTIVE),
				participant(2L, 10L, 101L, ParticipationStatus.COMPLETED),
				participant(3L, 10L, 102L, ParticipationStatus.EXPELLED),
				participant(4L, 10L, 103L, ParticipationStatus.EARLY_ENDED));
		when(results.findExistingParticipationIds(any())).thenReturn(Set.of());

		int saved = service.recordResults(List.of(
				new ResultInput(1L, 1, 1, 1, 1, false),
				new ResultInput(2L, 1, 1, 1, 1, true),
				new ResultInput(3L, 0, 1, 0, 0, false),
				new ResultInput(4L, 1, 1, 1, 1, false)), NOW);

		assertEquals(4, saved);
	}

	@Test
	void unknownParticipantIdIsRejected() {
		when(participations.findAllByIds(any())).thenReturn(List.of());
		when(results.findExistingParticipationIds(any())).thenReturn(Set.of());

		assertThrows(BusinessException.class,
				() -> service.recordResults(List.of(new ResultInput(99L, 1, 1, 0, 0, false)), NOW));
		verify(results, never()).save(any());
	}

	@Test
	void emptyInputDoesNothing() {
		assertEquals(0, service.recordResults(List.of(), NOW));
		verify(participations, never()).findAllByIds(any());
	}

	private void givenParticipants(Participation... rows) {
		when(participations.findAllByIds(any())).thenReturn(List.of(rows));
	}

	private static Participation participant(long id, long challengeId, long userId, ParticipationStatus status) {
		Participation participation = Participation.host(challengeId, userId, NOW);
		ReflectionTestUtils.setField(participation, "id", id);
		ReflectionTestUtils.setField(participation, "status", status);
		return participation;
	}
}

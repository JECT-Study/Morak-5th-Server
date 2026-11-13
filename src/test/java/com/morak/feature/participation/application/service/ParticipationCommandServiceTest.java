package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.feature.participation.application.command.ParticipationCommand.InvalidatedApplication;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.enums.ParticipationPointStatus;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ParticipationCommandServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");

	private final ParticipationRepository repository = mock(ParticipationRepository.class);
	private final ParticipationCommandService service = new ParticipationCommandService(repository);

	@Test
	void registerHostSavesActiveHost() {
		service.registerHost(10L, 1L, NOW);

		ArgumentCaptor<Participation> saved = ArgumentCaptor.forClass(Participation.class);
		verify(repository).save(saved.capture());
		assertEquals(ParticipationRole.HOST, saved.getValue().getRole());
		assertEquals(ParticipationStatus.ACTIVE, saved.getValue().getStatus());
		assertEquals(10L, saved.getValue().getChallengeId());
		assertEquals(1L, saved.getValue().getUserId());
	}

	@Test
	void invalidatesPendingApplicationsAndReturnsTheirHosts() {
		Participation first = pending(100L, 20L);
		Participation second = pending(101L, 21L);
		when(repository.findPendingByUserIdForUpdate(7L)).thenReturn(List.of(first, second));
		when(repository.findHosts(List.of(20L, 21L))).thenReturn(List.of(host(20L, 3L), host(21L, 4L)));

		Optional<List<InvalidatedApplication>> result = service.invalidatePendingWithoutPoints(7L);

		assertEquals(
				List.of(new InvalidatedApplication(100L, 20L, 3L), new InvalidatedApplication(101L, 21L, 4L)),
				result.orElseThrow());
		assertEquals(ParticipationStatus.INVALIDATED, first.getStatus());
		assertEquals(ParticipationStatus.INVALIDATED, second.getStatus());
	}

	@Test
	void returnsEmptyAndChangesNothingWhenAnyPendingApplicationHoldsPoints() {
		Participation plain = pending(100L, 20L);
		Participation held = pending(101L, 21L);
		ReflectionTestUtils.setField(held, "pointStatus", ParticipationPointStatus.HELD);
		when(repository.findPendingByUserIdForUpdate(7L)).thenReturn(List.of(plain, held));

		assertTrue(service.invalidatePendingWithoutPoints(7L).isEmpty());

		assertEquals(ParticipationStatus.PENDING, plain.getStatus());
		assertEquals(ParticipationStatus.PENDING, held.getStatus());
		verify(repository, never()).findHosts(any());
	}

	@Test
	void returnsEmptyListWhenThereIsNothingPending() {
		when(repository.findPendingByUserIdForUpdate(7L)).thenReturn(List.of());

		assertEquals(List.of(), service.invalidatePendingWithoutPoints(7L).orElseThrow());
	}

	private static Participation pending(long id, long challengeId) {
		Participation application = Participation.apply(challengeId, 7L, "각오", NOW);
		ReflectionTestUtils.setField(application, "id", id);
		return application;
	}

	private static Participation host(long challengeId, long userId) {
		return Participation.host(challengeId, userId, NOW);
	}
}

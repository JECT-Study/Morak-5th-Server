package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.command.ParticipationCommand.InvalidatedApplication;
import com.morak.feature.participation.application.command.ParticipationCommand.RoleHandOver;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationChallenges.ChallengeInfo;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.enums.ParticipationPointStatus;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import com.morak.feature.participation.domain.repository.ParticipationResultRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class ParticipationCommandServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");

	private final ParticipationRepository repository = mock(ParticipationRepository.class);
	private final ApplicationChallenges challenges = mock(ApplicationChallenges.class);
	private final com.morak.feature.participation.application.port.out.ApplicantPoints points =
			mock(com.morak.feature.participation.application.port.out.ApplicantPoints.class);
	private final ParticipationCommandService service = new ParticipationCommandService(
			repository, mock(ParticipationResultRepository.class), challenges, points,
			mock(com.morak.feature.participation.application.port.out.ApplicationNotifier.class));

	@Test
	void registerHostSavesActiveHost() {
		Participation host = Participation.host(10L, 1L, NOW);
		ReflectionTestUtils.setField(host, "id", 44L);
		when(repository.save(any(Participation.class))).thenReturn(host);
		assertEquals(44L, service.registerHost(10L, 1L, NOW, 0));

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

	// ---- 방장·부방장 승계 ----

	private static final long CHALLENGE = 10L;

	@Test
	void hostLeavingHandsHostToDeputyAndDeputyToEarliestMember() {
		Participation host = active(1L, 100L, ParticipationRole.HOST, 0);
		Participation deputy = active(2L, 200L, ParticipationRole.DEPUTY, 1);
		Participation early = active(3L, 300L, ParticipationRole.MEMBER, 2);
		Participation late = active(4L, 400L, ParticipationRole.MEMBER, 3);
		givenRoles(host, deputy, early, late);

		RoleHandOver result = service.handOverRoles(CHALLENGE, 1L);

		assertEquals(new RoleHandOver(200L, 300L, false), result);
		assertEquals(ParticipationRole.MEMBER, host.getRole());
		assertEquals(ParticipationRole.HOST, deputy.getRole());
		assertEquals(ParticipationRole.DEPUTY, early.getRole());
		assertEquals(ParticipationRole.MEMBER, late.getRole());
		verify(challenges).changeOwner(CHALLENGE, 200L);
	}

	@Test
	void hostLeavingLocksChallengeBeforeParticipants() {
		Participation host = active(1L, 100L, ParticipationRole.HOST, 0);
		Participation deputy = active(2L, 200L, ParticipationRole.DEPUTY, 1);
		givenRoles(host, deputy);

		service.handOverRoles(CHALLENGE, 1L);

		InOrder order = inOrder(challenges, repository);
		order.verify(challenges).lock(CHALLENGE);
		order.verify(repository).findByIdForUpdate(1L);
		order.verify(repository).findActiveByChallengeForUpdate(CHALLENGE);
		order.verify(challenges).changeOwner(CHALLENGE, 200L);
	}

	@Test
	void hostLeavingWithDeputyButNoOtherMemberLeavesDeputySlotEmpty() {
		Participation host = active(1L, 100L, ParticipationRole.HOST, 0);
		Participation deputy = active(2L, 200L, ParticipationRole.DEPUTY, 1);
		givenRoles(host, deputy);

		RoleHandOver result = service.handOverRoles(CHALLENGE, 1L);

		assertEquals(new RoleHandOver(200L, null, false), result);
		assertEquals(ParticipationRole.HOST, deputy.getRole());
	}

	@Test
	void hostLeavingWithoutDeputyChangesNothingAndReportsMissingSuccessor() {
		Participation host = active(1L, 100L, ParticipationRole.HOST, 0);
		Participation member = active(3L, 300L, ParticipationRole.MEMBER, 2);
		givenRoles(host, member);

		RoleHandOver result = service.handOverRoles(CHALLENGE, 1L);

		assertEquals(new RoleHandOver(null, null, true), result);
		assertEquals(ParticipationRole.HOST, host.getRole());
		assertEquals(ParticipationRole.MEMBER, member.getRole());
		verify(challenges, never()).changeOwner(anyLong(), anyLong());
	}

	@Test
	void deputyLeavingMakesEarliestMemberTheNewDeputyWithoutChangingOwner() {
		Participation host = active(1L, 100L, ParticipationRole.HOST, 0);
		Participation deputy = active(2L, 200L, ParticipationRole.DEPUTY, 1);
		Participation early = active(3L, 300L, ParticipationRole.MEMBER, 2);
		Participation late = active(4L, 400L, ParticipationRole.MEMBER, 3);
		givenRoles(host, deputy, early, late);

		RoleHandOver result = service.handOverRoles(CHALLENGE, 2L);

		assertEquals(new RoleHandOver(null, 300L, false), result);
		assertEquals(ParticipationRole.MEMBER, deputy.getRole());
		assertEquals(ParticipationRole.DEPUTY, early.getRole());
		assertEquals(ParticipationRole.HOST, host.getRole());
		verify(challenges, never()).changeOwner(anyLong(), anyLong());
	}

	@Test
	void deputyLeavingWithNoOtherMemberJustStepsDown() {
		Participation host = active(1L, 100L, ParticipationRole.HOST, 0);
		Participation deputy = active(2L, 200L, ParticipationRole.DEPUTY, 1);
		givenRoles(host, deputy);

		RoleHandOver result = service.handOverRoles(CHALLENGE, 2L);

		assertEquals(RoleHandOver.unchanged(), result);
		assertEquals(ParticipationRole.MEMBER, deputy.getRole());
	}

	@Test
	void plainMemberLeavingChangesNothingAndLocksNoOthers() {
		Participation member = active(3L, 300L, ParticipationRole.MEMBER, 2);
		givenRoles(member);

		RoleHandOver result = service.handOverRoles(CHALLENGE, 3L);

		assertEquals(RoleHandOver.unchanged(), result);
		verify(repository, never()).findActiveByChallengeForUpdate(anyLong());
		verify(challenges, never()).changeOwner(anyLong(), anyLong());
	}

	@Test
	void handOverOfParticipantFromAnotherChallengeIsNotFound() {
		Participation other = active(1L, 100L, ParticipationRole.HOST, 0);
		ReflectionTestUtils.setField(other, "challengeId", 11L);
		givenRoles(other);

		BusinessException error = assertThrows(BusinessException.class, () -> service.handOverRoles(CHALLENGE, 1L));

		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
	}

	@Test
	void handOverOfMissingChallengeIsNotFound() {
		when(challenges.lock(CHALLENGE)).thenReturn(Optional.empty());

		assertThrows(BusinessException.class, () -> service.handOverRoles(CHALLENGE, 1L));
		verify(repository, never()).findByIdForUpdate(anyLong());
	}

	/** 챌린지 잠금과, 합류 순서대로 정렬된 참여 중 참가자 목록을 준비한다. */
	private void givenRoles(Participation... participants) {
		ChallengeInfo info = new ChallengeInfo(CHALLENGE, 100L, "아침 독서", false, "", true, false, 0, 5);
		when(challenges.lock(CHALLENGE)).thenReturn(Optional.of(info));
		for (Participation participant : participants) {
			when(repository.findByIdForUpdate(participant.getId())).thenReturn(Optional.of(participant));
		}
		when(repository.findActiveByChallengeForUpdate(CHALLENGE)).thenReturn(List.of(participants));
	}

	private static Participation active(long id, long userId, ParticipationRole role, int joinedOrder) {
		Participation participant = Participation.apply(CHALLENGE, userId, "각오", NOW);
		participant.approve(NOW.plusSeconds(joinedOrder));
		ReflectionTestUtils.setField(participant, "id", id);
		ReflectionTestUtils.setField(participant, "role", role);
		return participant;
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

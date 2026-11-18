package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.application.port.out.ApplicantPoints;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationChallenges.ChallengeInfo;
import com.morak.feature.participation.application.port.out.ApplicationNotifier;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.enums.ParticipationPointStatus;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class DecideApplicationServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");
	private static final long HOST = 1L;
	private static final long APPLICANT = 7L;
	private static final long CHALLENGE = 10L;
	private static final long APPLICATION = 44L;

	private final ApplicationChallenges challenges = mock(ApplicationChallenges.class);
	private final ApplicantPoints points = mock(ApplicantPoints.class);
	private final ApplicationNotifier notifier = mock(ApplicationNotifier.class);
	private final ParticipationRepository participations = mock(ParticipationRepository.class);
	private final DecideApplicationService service = new DecideApplicationService(
			challenges, points, notifier, participations, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void approveActivatesApplicantIncreasesCountAndNotifiesApplicant() {
		Participation application = application(0);
		givenChallenge(challenge(true, false, 0));
		givenApplication(application);

		service.approve(HOST, CHALLENGE, APPLICATION);

		assertEquals(ParticipationStatus.ACTIVE, application.getStatus());
		verify(challenges).acceptMember(CHALLENGE);
		verify(notifier).approved(APPLICANT, CHALLENGE, APPLICATION, "아침 독서");
	}

	@Test
	void approveRejectsNonHost() {
		givenChallenge(challenge(true, false, 0));

		assertError(ParticipationErrorCode.NOT_CHALLENGE_HOST, () -> service.approve(99L, CHALLENGE, APPLICATION));
		verify(challenges, never()).acceptMember(anyLong());
		verify(notifier, never()).approved(anyLong(), anyLong(), anyLong(), anyString());
	}

	@Test
	void approveRejectsFullChallenge() {
		givenChallenge(challenge(true, true, 0));
		givenApplication(application(0));

		assertError(ParticipationErrorCode.CHALLENGE_FULL, () -> service.approve(HOST, CHALLENGE, APPLICATION));
		verify(challenges, never()).acceptMember(anyLong());
		verify(notifier, never()).approved(anyLong(), anyLong(), anyLong(), anyString());
	}

	@Test
	void approveRejectsChallengeThatIsNotRecruiting() {
		givenChallenge(challenge(false, false, 0));
		givenApplication(application(0));

		assertError(ParticipationErrorCode.CHALLENGE_NOT_RECRUITING, () -> service.approve(HOST, CHALLENGE, APPLICATION));
		verify(challenges, never()).acceptMember(anyLong());
	}

	@Test
	void approveRejectsApplicationThatIsNoLongerPending() {
		Participation invalidated = application(0);
		invalidated.invalidate();
		givenChallenge(challenge(true, true, 0));
		givenApplication(invalidated);

		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> service.approve(HOST, CHALLENGE, APPLICATION));
		verify(notifier, never()).approved(anyLong(), anyLong(), anyLong(), anyString());
	}

	@Test
	void approveRejectsApplicationOfAnotherChallenge() {
		givenChallenge(challenge(true, false, 0));
		Participation other = application(0);
		ReflectionTestUtils.setField(other, "challengeId", 11L);
		givenApplication(other);

		assertError(ParticipationErrorCode.APPLICATION_NOT_FOUND, () -> service.approve(HOST, CHALLENGE, APPLICATION));
	}

	@Test
	void rejectWithoutPointsNotifiesWithoutRefund() {
		Participation application = application(0);
		givenChallenge(challenge(true, false, 0));
		givenApplication(application);

		service.reject(HOST, CHALLENGE, APPLICATION);

		assertEquals(ParticipationStatus.REJECTED, application.getStatus());
		verify(points, never()).lockAccount(anyLong());
		verify(points, never()).release(anyLong(), anyLong(), anyString(), anyInt());
		verify(notifier).rejected(APPLICANT, CHALLENGE, APPLICATION, "아침 독서", 0);
	}

	@Test
	void rejectWithPointsLocksAccountFirstThenRefundsAllAndNotifiesAmount() {
		Participation application = application(1_000);
		givenChallenge(challenge(true, false, 1_000));
		givenApplication(application);

		service.reject(HOST, CHALLENGE, APPLICATION);

		InOrder order = inOrder(points, challenges);
		order.verify(points).lockAccount(APPLICANT);
		order.verify(challenges).lock(CHALLENGE);
		order.verify(points).release(APPLICANT, APPLICATION, "아침 독서", 1_000);
		assertEquals(ParticipationStatus.REJECTED, application.getStatus());
		verify(notifier).rejected(APPLICANT, CHALLENGE, APPLICATION, "아침 독서", 1_000);
	}

	@Test
	void rejectRejectsNonHostAndDoesNotRefund() {
		givenChallenge(challenge(true, false, 1_000));
		givenApplication(application(1_000));

		assertError(ParticipationErrorCode.NOT_CHALLENGE_HOST, () -> service.reject(99L, CHALLENGE, APPLICATION));
		verify(points, never()).release(anyLong(), anyLong(), anyString(), anyInt());
		verify(notifier, never()).rejected(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	void rejectUnknownApplicationIsNotFound() {
		when(participations.findById(APPLICATION)).thenReturn(Optional.empty());

		assertError(ParticipationErrorCode.APPLICATION_NOT_FOUND, () -> service.reject(HOST, CHALLENGE, APPLICATION));
	}

	@Test
	void rejectingTwiceIsRejectedAsInvalidTransitionWithoutSecondRefund() {
		Participation application = application(1_000);
		givenChallenge(challenge(true, false, 1_000));
		givenApplication(application);
		service.reject(HOST, CHALLENGE, APPLICATION);

		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> service.reject(HOST, CHALLENGE, APPLICATION));
		verify(points, org.mockito.Mockito.times(1)).release(anyLong(), anyLong(), anyString(), anyInt());
		assertEquals(ParticipationPointStatus.REFUNDED, pointStatusOf(application));
	}

	// ---- 첫 합류자 부방장 지정 (#8) ----

	@Test
	void firstMemberBecomesDeputyAndIsNotified() {
		Participation application = application(0);
		givenChallenge(challenge(true, false, 0));
		givenApplication(application);
		when(participations.existsActiveMemberExceptHost(CHALLENGE)).thenReturn(false);

		service.approve(HOST, CHALLENGE, APPLICATION);

		assertEquals(ParticipationRole.DEPUTY, application.getRole());
		InOrder order = inOrder(notifier);
		order.verify(notifier).approved(APPLICANT, CHALLENGE, APPLICATION, "아침 독서");
		order.verify(notifier).deputyAssigned(APPLICANT, CHALLENGE, APPLICATION, "아침 독서");
	}

	@Test
	void laterMemberStaysMemberAndGetsNoDeputyNotice() {
		Participation application = application(0);
		givenChallenge(challenge(true, false, 0));
		givenApplication(application);
		when(participations.existsActiveMemberExceptHost(CHALLENGE)).thenReturn(true);

		service.approve(HOST, CHALLENGE, APPLICATION);

		assertEquals(ParticipationRole.MEMBER, application.getRole());
		verify(notifier, never()).deputyAssigned(anyLong(), anyLong(), anyLong(), anyString());
	}

	@Test
	void failedApprovalDoesNotAssignDeputy() {
		Participation application = application(0);
		givenChallenge(challenge(true, true, 0));
		givenApplication(application);
		when(participations.existsActiveMemberExceptHost(CHALLENGE)).thenReturn(false);

		assertError(ParticipationErrorCode.CHALLENGE_FULL, () -> service.approve(HOST, CHALLENGE, APPLICATION));

		verify(notifier, never()).deputyAssigned(anyLong(), anyLong(), anyLong(), anyString());
	}

	// ---- 정원이 차면 남은 대기 신청 자동 거절 (#2) ----

	@Test
	void approvalThatFillsChallengeRejectsEveryRemainingApplicationAndRefundsPoints() {
		Participation approving = application(1_000);
		Participation heldHigh = pending(45L, 25L, 1_000);
		Participation free = pending(46L, 22L, 0);
		Participation heldLow = pending(47L, 21L, 1_000);
		ChallengeInfo filling = challenge(true, false, 1_000, 1);
		when(challenges.find(CHALLENGE)).thenReturn(Optional.of(filling));
		givenChallenge(filling);
		givenApplication(approving);
		when(participations.findPendingByChallenge(CHALLENGE)).thenReturn(List.of(approving, heldHigh, free, heldLow));
		when(participations.findPendingByChallengeForUpdate(CHALLENGE))
				.thenReturn(List.of(approving, heldHigh, free, heldLow));

		service.approve(HOST, CHALLENGE, APPLICATION);

		// 락 순서: 신청자 계정(사용자 번호 순) → 챌린지 → 환급
		InOrder order = inOrder(points, challenges);
		order.verify(points).lockAccount(21L);
		order.verify(points).lockAccount(25L);
		order.verify(challenges).lock(CHALLENGE);
		order.verify(points).release(25L, 45L, "아침 독서", 1_000);
		order.verify(points).release(21L, 47L, "아침 독서", 1_000);
		verify(points, never()).lockAccount(APPLICANT);
		assertEquals(ParticipationStatus.ACTIVE, approving.getStatus());
		assertEquals(ParticipationStatus.REJECTED, heldHigh.getStatus());
		assertEquals(ParticipationStatus.REJECTED, free.getStatus());
		assertEquals(ParticipationStatus.REJECTED, heldLow.getStatus());
		verify(notifier).rejected(25L, CHALLENGE, 45L, "아침 독서", 1_000);
		verify(notifier).rejected(22L, CHALLENGE, 46L, "아침 독서", 0);
		verify(notifier).rejected(21L, CHALLENGE, 47L, "아침 독서", 1_000);
		verify(points, never()).release(eq(22L), anyLong(), anyString(), anyInt());
		verify(notifier, never()).rejected(eq(APPLICANT), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	void approvalThatDoesNotFillChallengeLeavesOthersUntouched() {
		Participation approving = application(1_000);
		ChallengeInfo roomLeft = challenge(true, false, 1_000, 3);
		when(challenges.find(CHALLENGE)).thenReturn(Optional.of(roomLeft));
		givenChallenge(roomLeft);
		givenApplication(approving);

		service.approve(HOST, CHALLENGE, APPLICATION);

		verify(participations, never()).findPendingByChallenge(anyLong());
		verify(participations, never()).findPendingByChallengeForUpdate(anyLong());
		verify(points, never()).lockAccount(anyLong());
		verify(notifier, never()).rejected(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	void fillingApprovalWithoutPendingApplicationsRejectsNobody() {
		Participation approving = application(0);
		ChallengeInfo filling = challenge(true, false, 0, 1);
		when(challenges.find(CHALLENGE)).thenReturn(Optional.of(filling));
		givenChallenge(filling);
		givenApplication(approving);
		when(participations.findPendingByChallenge(CHALLENGE)).thenReturn(List.of(approving));
		when(participations.findPendingByChallengeForUpdate(CHALLENGE)).thenReturn(List.of(approving));

		service.approve(HOST, CHALLENGE, APPLICATION);

		assertEquals(ParticipationStatus.ACTIVE, approving.getStatus());
		verify(notifier, never()).rejected(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	void applicantWhoAppearedAfterAccountLockingAbortsTheApprovalAndRefundsNothing() {
		Participation approving = application(1_000);
		Participation latecomer = pending(48L, 30L, 1_000);
		ChallengeInfo filling = challenge(true, false, 1_000, 1);
		when(challenges.find(CHALLENGE)).thenReturn(Optional.of(filling));
		givenChallenge(filling);
		givenApplication(approving);
		when(participations.findPendingByChallenge(CHALLENGE)).thenReturn(List.of(approving));
		when(participations.findPendingByChallengeForUpdate(CHALLENGE)).thenReturn(List.of(approving, latecomer));

		assertError(ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED, () -> service.approve(HOST, CHALLENGE, APPLICATION));

		verify(points, never()).release(anyLong(), anyLong(), anyString(), anyInt());
		verify(notifier, never()).rejected(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
		assertEquals(ParticipationStatus.PENDING, latecomer.getStatus());
	}

	@Test
	void staleReadThatMissedTheFillingMomentAlsoAbortsWhenPointsWereNotLocked() {
		Participation approving = application(1_000);
		Participation held = pending(45L, 25L, 1_000);
		when(challenges.find(CHALLENGE)).thenReturn(Optional.of(challenge(true, false, 1_000, 3)));
		givenChallenge(challenge(true, false, 1_000, 1));
		givenApplication(approving);
		when(participations.findPendingByChallengeForUpdate(CHALLENGE)).thenReturn(List.of(approving, held));

		assertError(ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED, () -> service.approve(HOST, CHALLENGE, APPLICATION));

		verify(points, never()).release(anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	void applicationsWithoutHeldPointsAreRejectedEvenWhenAccountsWereNotLocked() {
		Participation approving = application(0);
		Participation free = pending(46L, 22L, 0);
		when(challenges.find(CHALLENGE)).thenReturn(Optional.of(challenge(true, false, 0, 3)));
		givenChallenge(challenge(true, false, 0, 1));
		givenApplication(approving);
		when(participations.findPendingByChallengeForUpdate(CHALLENGE)).thenReturn(List.of(approving, free));

		service.approve(HOST, CHALLENGE, APPLICATION);

		assertEquals(ParticipationStatus.REJECTED, free.getStatus());
		verify(notifier).rejected(22L, CHALLENGE, 46L, "아침 독서", 0);
	}

	// ---- 무효 신청 확인 ----

	@Test
	void confirmInvalidationRecordsConfirmationForInvalidatedApplication() {
		Participation invalidated = application(0);
		invalidated.invalidate();
		givenChallenge(challenge(true, false, 0));
		givenApplication(invalidated);

		service.confirmInvalidation(HOST, CHALLENGE, APPLICATION);

		assertEquals(java.time.LocalDateTime.of(2026, 11, 17, 1, 0), invalidated.getInvalidConfirmedAt());
	}

	@Test
	void confirmInvalidationIsIdempotent() {
		Participation invalidated = application(0);
		invalidated.invalidate();
		givenChallenge(challenge(true, false, 0));
		givenApplication(invalidated);

		service.confirmInvalidation(HOST, CHALLENGE, APPLICATION);
		service.confirmInvalidation(HOST, CHALLENGE, APPLICATION);

		assertEquals(java.time.LocalDateTime.of(2026, 11, 17, 1, 0), invalidated.getInvalidConfirmedAt());
	}

	@Test
	void confirmInvalidationRejectsNonHost() {
		givenChallenge(challenge(true, false, 0));
		Participation invalidated = application(0);
		invalidated.invalidate();
		givenApplication(invalidated);

		assertError(ParticipationErrorCode.NOT_CHALLENGE_HOST, () -> service.confirmInvalidation(99L, CHALLENGE, APPLICATION));
		assertNull(invalidated.getInvalidConfirmedAt());
	}

	@Test
	void confirmInvalidationRejectsApplicationThatIsNotInvalidated() {
		givenChallenge(challenge(true, false, 0));
		givenApplication(application(0));

		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> service.confirmInvalidation(HOST, CHALLENGE, APPLICATION));
	}

	@Test
	void confirmInvalidationOfAnotherChallengesApplicationIsNotFound() {
		givenChallenge(challenge(true, false, 0));
		Participation other = application(0);
		other.invalidate();
		ReflectionTestUtils.setField(other, "challengeId", 11L);
		givenApplication(other);

		assertError(ParticipationErrorCode.APPLICATION_NOT_FOUND, () -> service.confirmInvalidation(HOST, CHALLENGE, APPLICATION));
	}

	@Test
	void invalidatedApplicationCannotBeApprovedEvenBeforeConfirmation() {
		Participation invalidated = application(0);
		invalidated.invalidate();
		givenChallenge(challenge(true, false, 0));
		givenApplication(invalidated);

		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> service.approve(HOST, CHALLENGE, APPLICATION));
	}

	private void givenChallenge(ChallengeInfo info) {
		when(challenges.lock(CHALLENGE)).thenReturn(Optional.of(info));
	}

	private void givenApplication(Participation application) {
		when(participations.findById(APPLICATION)).thenReturn(Optional.of(application));
		when(participations.findByIdForUpdate(APPLICATION)).thenReturn(Optional.of(application));
	}

	private static Participation application(int pointAmount) {
		Participation application = Participation.apply(CHALLENGE, APPLICANT, "열심히 할게요", pointAmount, NOW);
		ReflectionTestUtils.setField(application, "id", APPLICATION);
		return application;
	}

	private static Participation pending(long id, long userId, int pointAmount) {
		Participation pending = Participation.apply(CHALLENGE, userId, "신청", pointAmount, NOW);
		ReflectionTestUtils.setField(pending, "id", id);
		return pending;
	}

	private static ChallengeInfo challenge(boolean recruiting, boolean full, int pointAmount) {
		return challenge(recruiting, full, pointAmount, full ? 0 : 5);
	}

	private static ChallengeInfo challenge(boolean recruiting, boolean full, int pointAmount, int remainingSlots) {
		return new ChallengeInfo(CHALLENGE, HOST, "아침 독서", false, "", recruiting, full, pointAmount, remainingSlots);
	}

	private static ParticipationPointStatus pointStatusOf(Participation participation) {
		return (ParticipationPointStatus) ReflectionTestUtils.getField(participation, "pointStatus");
	}

	private static void assertError(ParticipationErrorCode expected, Runnable action) {
		BusinessException error = assertThrows(BusinessException.class, action::run);
		assertEquals(expected, error.getErrorCode());
	}
}

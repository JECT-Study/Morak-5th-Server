package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

	private static ChallengeInfo challenge(boolean recruiting, boolean full, int pointAmount) {
		return new ChallengeInfo(CHALLENGE, HOST, "아침 독서", false, "", recruiting, full, pointAmount);
	}

	private static ParticipationPointStatus pointStatusOf(Participation participation) {
		return (ParticipationPointStatus) ReflectionTestUtils.getField(participation, "pointStatus");
	}

	private static void assertError(ParticipationErrorCode expected, Runnable action) {
		BusinessException error = assertThrows(BusinessException.class, action::run);
		assertEquals(expected, error.getErrorCode());
	}
}

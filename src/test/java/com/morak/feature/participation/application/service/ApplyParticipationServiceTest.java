package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.port.out.ApplicantPoints;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationChallenges.ChallengeInfo;
import com.morak.feature.participation.application.port.out.ApplicationNotifier;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class ApplyParticipationServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");
	private static final long USER = 7L;
	private static final long CHALLENGE = 10L;

	private final ApplicationChallenges challenges = mock(ApplicationChallenges.class);
	private final ApplicantPoints points = mock(ApplicantPoints.class);
	private final ApplicationNotifier notifier = mock(ApplicationNotifier.class);
	private final ParticipationRepository participations = mock(ParticipationRepository.class);
	private final ApplyParticipationService service = new ApplyParticipationService(
			challenges, points, notifier, participations, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void appliesToAChallengeWithoutPointsAndStoresPendingMember() {
		givenChallenge(info(false, "", true, 0));
		givenSavedWithId(44L);
		when(challenges.canJoinMore(USER)).thenReturn(true);

		long id = service.apply(USER, CHALLENGE, "열심히 할게요", null);

		assertEquals(44L, id);
		Participation saved = savedParticipation();
		assertEquals(ParticipationStatus.PENDING, saved.getStatus());
		assertFalse(saved.isPointHeld());
		verify(points, never()).lockAccount(anyLong());
		verify(points, never()).hold(anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	void locksPointAccountBeforeChallengeAndHoldsPointsForPointChallenge() {
		givenChallenge(info(false, "", true, 1_000));
		givenSavedWithId(44L);
		when(challenges.canJoinMore(USER)).thenReturn(true);

		service.apply(USER, CHALLENGE, null, null);

		InOrder order = inOrder(points, challenges);
		order.verify(points).lockAccount(USER);
		order.verify(challenges).lock(CHALLENGE);
		verify(points).hold(USER, 44L, "아침 독서", 1_000);
		assertEquals(true, savedParticipation().isPointHeld());
		assertEquals(1_000, savedParticipation().getPointAmount());
	}

	@Test
	void notifiesTheHostWhenAnApplicationIsReceived() {
		givenChallenge(info(false, "", true, 1_000));
		givenSavedWithId(44L);
		when(challenges.canJoinMore(USER)).thenReturn(true);

		service.apply(USER, CHALLENGE, "열심히 할게요", null);

		// 방장은 info()의 ownerUserId(1L)이고, 신청자(USER)가 아니다.
		verify(notifier).requested(1L, CHALLENGE, 44L, "아침 독서");
	}

	@Test
	void doesNotNotifyWhenTheApplicationIsRejected() {
		givenChallenge(info(false, "", true, 0));
		when(challenges.canJoinMore(USER)).thenReturn(false);

		assertError(ParticipationErrorCode.PARTICIPATION_LIMIT_EXCEEDED, () -> service.apply(USER, CHALLENGE, null, null));

		verify(notifier, never()).requested(anyLong(), anyLong(), anyLong(), anyString());
	}

	@Test
	void rejectsFullChallengeBecauseRecruitmentIsClosed() {
		givenChallenge(new ChallengeInfo(CHALLENGE, 1L, "아침 독서", false, "", true, true, 0, 0));
		when(challenges.canJoinMore(USER)).thenReturn(true);

		assertError(ParticipationErrorCode.CHALLENGE_FULL, () -> service.apply(USER, CHALLENGE, null, null));
		verify(participations, never()).save(any());
	}

	@Test
	void rejectsUnknownChallenge() {
		when(challenges.find(CHALLENGE)).thenReturn(Optional.empty());

		BusinessException error = assertThrows(BusinessException.class, () -> service.apply(USER, CHALLENGE, null, null));

		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
	}

	@Test
	void rejectsChallengeThatIsNotRecruiting() {
		givenChallenge(info(false, "", false, 0));

		assertError(ParticipationErrorCode.CHALLENGE_NOT_RECRUITING, () -> service.apply(USER, CHALLENGE, null, null));
		verify(participations, never()).save(any());
	}

	@Test
	void privateChallengeRequiresMatchingInviteCode() {
		givenChallenge(info(true, "123456", true, 0));
		when(challenges.canJoinMore(USER)).thenReturn(true);

		assertError(ParticipationErrorCode.INVALID_INVITE_CODE, () -> service.apply(USER, CHALLENGE, null, null));
		assertError(ParticipationErrorCode.INVALID_INVITE_CODE, () -> service.apply(USER, CHALLENGE, null, "000000"));
		verify(participations, never()).save(any());
	}

	@Test
	void privateChallengeAcceptsMatchingInviteCode() {
		givenChallenge(info(true, "123456", true, 0));
		givenSavedWithId(44L);
		when(challenges.canJoinMore(USER)).thenReturn(true);

		assertEquals(44L, service.apply(USER, CHALLENGE, null, "123456"));
	}

	@Test
	void rejectsWhenConcurrentParticipationLimitIsReached() {
		givenChallenge(info(false, "", true, 0));
		when(challenges.canJoinMore(USER)).thenReturn(false);

		assertError(ParticipationErrorCode.PARTICIPATION_LIMIT_EXCEEDED, () -> service.apply(USER, CHALLENGE, null, null));
		verify(participations, never()).save(any());
	}

	private void givenChallenge(ChallengeInfo info) {
		when(challenges.find(CHALLENGE)).thenReturn(Optional.of(info));
		when(challenges.lock(CHALLENGE)).thenReturn(Optional.of(info));
	}

	private void givenSavedWithId(long id) {
		when(participations.save(any(Participation.class))).thenAnswer(invocation -> {
			Participation participation = invocation.getArgument(0);
			ReflectionTestUtils.setField(participation, "id", id);
			return participation;
		});
	}

	private Participation savedParticipation() {
		ArgumentCaptor<Participation> saved = ArgumentCaptor.forClass(Participation.class);
		verify(participations).save(saved.capture());
		return saved.getValue();
	}

	private static ChallengeInfo info(boolean isPrivate, String inviteCode, boolean recruiting, int pointAmount) {
		return new ChallengeInfo(CHALLENGE, 1L, "아침 독서", isPrivate, inviteCode, recruiting, false, pointAmount, 5);
	}

	private static void assertError(ParticipationErrorCode expected, Runnable action) {
		BusinessException error = assertThrows(BusinessException.class, action::run);
		assertEquals(expected, error.getErrorCode());
	}
}

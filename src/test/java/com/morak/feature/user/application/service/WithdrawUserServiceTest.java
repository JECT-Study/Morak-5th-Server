package com.morak.feature.user.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.user.application.port.out.LoginSessionRevoker;
import com.morak.feature.user.application.port.out.OngoingParticipationReader;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.user.domain.vo.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WithdrawUserServiceTest {

	private static final UserId USER_ID = new UserId(7L);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneOffset.UTC);

	private final UserRepository users = mock(UserRepository.class);
	private final OngoingParticipationReader participations = mock(OngoingParticipationReader.class);
	private final LoginSessionRevoker sessions = mock(LoginSessionRevoker.class);
	private final ProfileImageService profileImages = mock(ProfileImageService.class);
	private final WithdrawUserService service = new WithdrawUserService(users, participations, sessions, CLOCK, profileImages);

	@Test
	void withdrawsAndRevokesSessionsWhenNoOngoingChallenge() {
		User user = user();
		String photo = "https://example.com/profiles/users/7/avatar";
		user.changeProfileImage(photo);
		when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
		when(participations.countOngoing(7L)).thenReturn(0);

		TransactionSynchronizationManager.initSynchronization();
		try {
			service.withdraw(USER_ID);
			assertNull(user.getProfileImageUrl());
			verifyNoInteractions(profileImages);
			TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
			verify(profileImages).deleteUploadedImage(photo);
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}

		assertFalse(user.isActive());
		assertEquals(LocalDateTime.of(2026, 11, 17, 3, 0), user.getDeletedAt());
		verify(sessions).revokeAll(7L, LocalDateTime.of(2026, 11, 17, 3, 0));
	}

	@Test
	void locksUserBeforeCountingOngoingChallenges() {
		when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user()));
		when(participations.countOngoing(7L)).thenReturn(0);

		service.withdraw(USER_ID);

		var order = inOrder(users, participations);
		order.verify(users).findByIdForUpdate(USER_ID);
		order.verify(participations).countOngoing(7L);
	}

	@Test
	void rejectsWhenOngoingChallengeExistsAndKeepsAccountUntouched() {
		User user = user();
		when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
		when(participations.countOngoing(7L)).thenReturn(1);

		BusinessException error = assertThrows(BusinessException.class, () -> service.withdraw(USER_ID));

		assertEquals(UserErrorCode.WITHDRAWAL_NOT_ALLOWED, error.getErrorCode());
		assertTrue(user.isActive());
		assertEquals("a@example.com", user.getEmail());
		verifyNoInteractions(sessions);
	}

	@Test
	void rejectsMissingOrAlreadyWithdrawnUser() {
		when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND,
				assertThrows(BusinessException.class, () -> service.withdraw(USER_ID)).getErrorCode());

		User withdrawn = user();
		ReflectionTestUtils.setField(withdrawn, "deletedAt", LocalDateTime.now());
		when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(withdrawn));
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND,
				assertThrows(BusinessException.class, () -> service.withdraw(USER_ID)).getErrorCode());
		verifyNoInteractions(participations, sessions);
	}

	private User user() {
		User user = new User("a@example.com", null);
		ReflectionTestUtils.setField(user, "id", 7L);
		return user;
	}
}

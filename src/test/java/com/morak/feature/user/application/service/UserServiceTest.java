package com.morak.feature.user.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.user.application.port.out.SignupPointGranter;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.user.domain.vo.OnboardingProfile;
import com.morak.feature.user.domain.vo.UserId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

	private final UserRepository repository = mock(UserRepository.class);
	private final SignupPointGranter signupPointGranter = mock(SignupPointGranter.class);
	private final UserService service = new UserService(repository, signupPointGranter);

	@Test
	void completingOnboardingGrantsSignupPointAfterSavingProfile() {
		User user = persistedUser();
		ReflectionTestUtils.setField(user, "termsAgreedAt", LocalDateTime.now());
		when(repository.findByIdForUpdate(new UserId(7L))).thenReturn(Optional.of(user));

		var result = service.completeOnboarding(new UserId(7L), profile("Morak", "STUDY"));

		assertTrue(result.onboarded());
		var order = inOrder(repository, signupPointGranter);
		order.verify(repository).findByIdForUpdate(new UserId(7L));
		order.verify(repository).flushOnboarding();
		order.verify(signupPointGranter).grant(7L);
	}

	@Test
	void secondOnboardingIsRejectedWithoutGrantingAgain() {
		User user = persistedUser();
		ReflectionTestUtils.setField(user, "termsAgreedAt", LocalDateTime.now());
		ReflectionTestUtils.setField(user, "onboardedAt", LocalDateTime.now());
		when(repository.findByIdForUpdate(new UserId(7L))).thenReturn(Optional.of(user));

		var exception = assertThrows(BusinessException.class,
				() -> service.completeOnboarding(new UserId(7L), profile("Morak", "STUDY")));

		assertEquals(UserErrorCode.ALREADY_ONBOARDED, exception.getErrorCode());
		verifyNoInteractions(signupPointGranter);
	}

	@Test
	void onboardingWithoutTermsDoesNotGrant() {
		when(repository.findByIdForUpdate(new UserId(7L))).thenReturn(Optional.of(persistedUser()));
		assertThrows(BusinessException.class,
				() -> service.completeOnboarding(new UserId(7L), profile("Morak", "STUDY")));
		verifyNoInteractions(signupPointGranter);
	}

	@Test
	void updatesProfileFieldsWithoutGrantingAndKeepsOnboardedAt() {
		User user = persistedUser();
		LocalDateTime onboardedAt = LocalDateTime.of(2026, 11, 1, 0, 0);
		ReflectionTestUtils.setField(user, "onboardedAt", onboardedAt);
		ReflectionTestUtils.setField(user, "bio", "old");
		when(repository.findByIdForUpdate(new UserId(7L))).thenReturn(Optional.of(user));

		var result = service.updateProfile(new UserId(7L),
				OnboardingProfile.from("NewName", null, "https://example.com/me", List.of("READING", "CAREER")));

		assertEquals("NewName", result.nickname());
		assertNull(user.getBio());
		assertEquals("https://example.com/me", user.getProfileLinkUrl());
		assertEquals(Set.of("READING", "CAREER"), ReflectionTestUtils.getField(user, "interests"));
		assertEquals(onboardedAt, user.getOnboardedAt());
		assertEquals("https://example.com/old.png", user.getProfileImageUrl());
		verify(repository).flushOnboarding();
		verifyNoInteractions(signupPointGranter);
	}

	@Test
	void withdrawnUserCannotUpdateProfile() {
		User user = persistedUser();
		ReflectionTestUtils.setField(user, "deletedAt", LocalDateTime.now());
		when(repository.findByIdForUpdate(new UserId(7L))).thenReturn(Optional.of(user));
		assertThrows(BusinessException.class, () -> service.updateProfile(new UserId(7L), profile("NewName", "STUDY")));
		assertEquals("old@example.com", user.getEmail());
		verify(repository, never()).flushOnboarding();
	}

	private static OnboardingProfile profile(String nickname, String interest) {
		return OnboardingProfile.from(nickname, null, null, List.of(interest));
	}

	@Test
	void createsUserAndReturnsPersistedIdentityWithoutExposingEntity() {
		when(repository.save(any())).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			ReflectionTestUtils.setField(user, "id", 7L);
			return user;
		});
		var result = service.createUser("new@example.com", "https://example.com/new.png");
		assertEquals(new UserId(7L), result.userId());
		assertTrue(result.active());
		assertFalse(result.onboarded());
		var captor = ArgumentCaptor.forClass(User.class);
		verify(repository).save(captor.capture());
		assertEquals("new@example.com", captor.getValue().getEmail());
		assertEquals("https://example.com/new.png", captor.getValue().getProfileImageUrl());
	}

	@Test
	void missingUserReturnsEmptyWithoutCreatingOne() {
		when(repository.findById(new UserId(7L))).thenReturn(Optional.empty());
		when(repository.findByIdForUpdate(new UserId(7L))).thenReturn(Optional.empty());
		assertTrue(service.findUser(new UserId(7L)).isEmpty());
		assertTrue(service.updateSocialProfile(new UserId(7L), "new@example.com", null).isEmpty());
		verify(repository, never()).save(any());
	}

	@Test
	void updatesActiveUserAndPreservesExistingProfileWhenProviderOmitsValues() {
		User user = persistedUser();
		ReflectionTestUtils.setField(user, "onboardedAt", LocalDateTime.now());
		when(repository.findById(new UserId(7L))).thenReturn(Optional.of(user));
		when(repository.findByIdForUpdate(new UserId(7L))).thenReturn(Optional.of(user));
		var result = service.updateSocialProfile(new UserId(7L), "new@example.com", " ").orElseThrow();
		assertEquals("new@example.com", user.getEmail());
		assertEquals("https://example.com/old.png", user.getProfileImageUrl());
		assertTrue(result.onboarded());
		assertTrue(service.findUser(new UserId(7L)).orElseThrow().onboarded());
	}

	@Test
	void doesNotUpdateWithdrawnUserProfile() {
		User user = persistedUser();
		ReflectionTestUtils.setField(user, "deletedAt", LocalDateTime.now());
		when(repository.findByIdForUpdate(new UserId(7L))).thenReturn(Optional.of(user));
		var result = service.updateSocialProfile(new UserId(7L), "new@example.com", null).orElseThrow();
		assertFalse(result.active());
		assertEquals("old@example.com", user.getEmail());
	}

	private User persistedUser() {
		User user = new User("old@example.com", "https://example.com/old.png");
		ReflectionTestUtils.setField(user, "id", 7L);
		return user;
	}
}

package com.morak.feature.user.application.service;

import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.user.domain.vo.UserId;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

	private final UserRepository repository = mock(UserRepository.class);
	private final UserService service = new UserService(repository);

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
		assertTrue(service.findUser(new UserId(7L)).isEmpty());
		assertTrue(service.updateSocialProfile(new UserId(7L), "new@example.com", null).isEmpty());
		verify(repository, never()).save(any());
	}

	@Test
	void updatesActiveUserAndPreservesExistingProfileWhenProviderOmitsValues() {
		User user = persistedUser();
		ReflectionTestUtils.setField(user, "onboardedAt", LocalDateTime.now());
		when(repository.findById(new UserId(7L))).thenReturn(Optional.of(user));
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
		when(repository.findById(new UserId(7L))).thenReturn(Optional.of(user));
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

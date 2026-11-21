package com.morak.feature.user.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.user.application.dto.result.UserProfileView;
import com.morak.feature.user.application.port.out.UserRecordReader;
import com.morak.feature.user.application.port.out.UserRecordReader.Medal;
import com.morak.feature.user.application.port.out.UserRecordReader.UserRecord;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.user.domain.vo.UserId;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReadUserProfileServiceTest {

	private static final UserId USER_ID = new UserId(7L);

	private final UserRepository users = mock(UserRepository.class);
	private final UserRecordReader records = mock(UserRecordReader.class);
	private final ReadUserProfileService service = new ReadUserProfileService(users, records);

	@Test
	void returnsPublicProfileWithRecord() {
		when(users.findById(USER_ID)).thenReturn(Optional.of(onboardedUser()));
		List<Medal> medals = List.of(new Medal("COMPLETE_1", "메달 1-1", "https://example.com/m.png"));
		when(records.read(7L)).thenReturn(new UserRecord(80, 3, true, 3, 1, medals));

		UserProfileView profile = service.read(7L);

		assertEquals(7L, profile.userId());
		assertEquals("러너", profile.nickname());
		assertEquals("https://example.com/p.png", profile.profileImageUrl());
		assertEquals("매일 달려요", profile.bio());
		assertEquals("https://example.com", profile.profileLinkUrl());
		assertEquals(List.of("EXERCISE", "READING"), profile.interests());
		assertEquals(80, profile.certificationRate());
		assertEquals(3, profile.morakiStage());
		assertEquals(3, profile.completedCount());
		assertEquals(1, profile.midwayFailCount());
		assertEquals(medals, profile.medals());
	}

	@Test
	void unknownUserIsNotFound() {
		when(users.findById(USER_ID)).thenReturn(Optional.empty());
		assertNotFound();
	}

	@Test
	void withdrawnUserIsNotFound() {
		User user = onboardedUser();
		ReflectionTestUtils.setField(user, "status", "WITHDRAWN");
		ReflectionTestUtils.setField(user, "deletedAt", LocalDateTime.now());
		when(users.findById(USER_ID)).thenReturn(Optional.of(user));
		assertNotFound();
	}

	@Test
	void userBeforeOnboardingIsNotFound() {
		User user = onboardedUser();
		ReflectionTestUtils.setField(user, "onboardedAt", null);
		when(users.findById(USER_ID)).thenReturn(Optional.of(user));
		assertNotFound();
	}

	private void assertNotFound() {
		BusinessException exception = assertThrows(BusinessException.class, () -> service.read(7L));
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(records);
	}

	private User onboardedUser() {
		User user = new User("runner@example.com", "https://example.com/p.png");
		ReflectionTestUtils.setField(user, "id", 7L);
		ReflectionTestUtils.setField(user, "nickname", "러너");
		ReflectionTestUtils.setField(user, "bio", "매일 달려요");
		ReflectionTestUtils.setField(user, "profileLinkUrl", "https://example.com");
		ReflectionTestUtils.setField(user, "interests", new HashSet<>(Set.of("READING", "EXERCISE")));
		ReflectionTestUtils.setField(user, "onboardedAt", LocalDateTime.now());
		return user;
	}
}

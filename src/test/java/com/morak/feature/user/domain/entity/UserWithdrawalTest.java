package com.morak.feature.user.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserWithdrawalTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 3, 0);

	@Test
	void anonymizesPersonalDataAndMarksWithdrawn() {
		User user = onboardedUser();

		user.withdraw(NOW);

		assertNull(user.getEmail());
		assertNull(user.getNickname());
		assertNull(user.getProfileImageUrl());
		assertNull(user.getBio());
		assertNull(user.getProfileLinkUrl());
		assertTrue(((Set<?>) ReflectionTestUtils.getField(user, "interests")).isEmpty());
		// 마케팅 수신 동의는 위키에 정해진 바가 없어 바꾸지 않는다(O-18).
		assertTrue(user.isMarketingAgreed());
		assertEquals("WITHDRAWN", user.getStatus());
		assertEquals(NOW, user.getDeletedAt());
		assertFalse(user.isActive());
	}

	@Test
	void keepsTermsAgreementRecord() {
		User user = onboardedUser();
		LocalDateTime agreedAt = (LocalDateTime) ReflectionTestUtils.getField(user, "termsAgreedAt");

		user.withdraw(NOW);

		assertEquals(agreedAt, ReflectionTestUtils.getField(user, "termsAgreedAt"));
	}

	@Test
	void rejectsSecondWithdrawalAndKeepsFirstTimestamp() {
		User user = onboardedUser();
		user.withdraw(NOW);

		BusinessException error = assertThrows(BusinessException.class, () -> user.withdraw(NOW.plusDays(1)));

		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
		assertEquals(NOW, user.getDeletedAt());
	}

	private User onboardedUser() {
		User user = new User("a@example.com", "https://example.com/a.png");
		ReflectionTestUtils.setField(user, "id", 7L);
		ReflectionTestUtils.setField(user, "nickname", "Morak");
		ReflectionTestUtils.setField(user, "bio", "소개");
		ReflectionTestUtils.setField(user, "profileLinkUrl", "https://example.com");
		ReflectionTestUtils.setField(user, "interests", new HashSet<>(Set.of("STUDY", "EXERCISE")));
		ReflectionTestUtils.setField(user, "marketingAgreed", true);
		ReflectionTestUtils.setField(user, "termsAgreedAt", NOW.minusDays(3));
		ReflectionTestUtils.setField(user, "onboardedAt", NOW.minusDays(3));
		return user;
	}
}

package com.morak.feature.user.application.dto.result;

import com.morak.feature.user.domain.entity.User;

public record MyUserInfo(
		Long userId,
		String email,
		String nickname,
		String profileImageUrl,
		boolean termsAgreed,
		boolean marketingAgreed,
		boolean onboarded) {

	public static MyUserInfo from(User user) {
		return new MyUserInfo(user.getId(), user.getEmail(), user.getNickname(), user.getProfileImageUrl(),
				user.hasAgreedToTerms(), user.isMarketingAgreed(), user.isOnboarded());
	}
}

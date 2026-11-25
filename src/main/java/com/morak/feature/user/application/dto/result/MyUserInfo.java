package com.morak.feature.user.application.dto.result;

import com.morak.feature.user.domain.entity.User;
import java.util.List;

public record MyUserInfo(
		Long userId,
		String email,
		String nickname,
		String profileImageUrl,
		String bio,
		String profileLinkUrl,
		List<String> interests,
		boolean termsAgreed,
		boolean marketingAgreed,
		boolean onboarded) {

	public static MyUserInfo from(User user) {
		return new MyUserInfo(user.userId().value(), user.getEmail(), user.getNickname(), user.getProfileImageUrl(),
				user.getBio(), user.getProfileLinkUrl(), user.interestCodes(), user.hasAgreedToTerms(), user.isMarketingAgreed(), user.isOnboarded());
	}
}

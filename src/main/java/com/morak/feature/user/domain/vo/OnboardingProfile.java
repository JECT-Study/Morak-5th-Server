package com.morak.feature.user.domain.vo;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record OnboardingProfile(Nickname nickname, String bio, String profileLinkUrl, Set<Interest> interests) {

	public OnboardingProfile {
		if (nickname == null || (bio != null && bio.codePointCount(0, bio.length()) > 255)
				|| (profileLinkUrl != null && !isValidProfileLink(profileLinkUrl))
				|| interests == null || interests.isEmpty()) {
			throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
		}
		interests = Set.copyOf(interests);
	}

	public static OnboardingProfile from(String nickname, String bio, String profileLinkUrl, List<String> codes) {
		Set<Interest> interests = new HashSet<>();
		if (codes != null) {
			for (String code : codes) {
				try {
					interests.add(Interest.valueOf(code));
				} catch (IllegalArgumentException | NullPointerException exception) {
					throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
				}
			}
		}
		return new OnboardingProfile(new Nickname(nickname), bio, profileLinkUrl, interests);
	}

	private static boolean isValidProfileLink(String value) {
		if (value.length() > 255) {
			return false;
		}
		try {
			URI uri = URI.create(value);
			return uri.getHost() != null
					&& ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}

package com.morak.feature.user.domain.vo;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import java.net.IDN;
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
		if (value.codePointCount(0, value.length()) > 255) {
			return false;
		}
		try {
			URI uri = URI.create(value);
			return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
					&& (uri.getHost() != null || hasValidInternationalHost(uri.getRawAuthority()));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private static boolean hasValidInternationalHost(String authority) {
		if (authority == null) {
			return false;
		}
		int hostStart = authority.indexOf('@') + 1;
		int portStart = authority.indexOf(':', hostStart);
		int hostEnd = portStart < 0 ? authority.length() : portStart;
		String host = authority.substring(hostStart, hostEnd);
		if (host.chars().allMatch(character -> character <= 0x7f)) {
			return false;
		}
		String asciiHost = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
		String asciiAuthority = authority.substring(0, hostStart) + asciiHost + authority.substring(hostEnd);
		return URI.create("https://" + asciiAuthority).getHost() != null;
	}
}

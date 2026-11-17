package com.morak.feature.auth.infrastructure.oauth;

import com.morak.feature.auth.application.port.out.OAuthProviderException;
import com.morak.feature.auth.application.port.out.SocialOAuthProvider;
import com.morak.feature.auth.application.port.out.SocialProfile;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발·테스트 전용 로그인 제공자. 카카오 없이 `POST /auth/login/dev`로 계정을 만든다.
 *
 * <p>요청의 `code`를 제공자 쪽 유저 식별자로 그대로 쓴다. 같은 code는 같은 계정, 새 code는 새 계정(온보딩 전)이다.
 * `local` 프로필에서만 빈으로 등록되므로 다른 환경에서는 `dev` provider가 지원되지 않는다(400).
 */
@Component
@Profile("local")
public class DevSocialOAuthProvider implements SocialOAuthProvider {

	private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	@Override
	public String provider() {
		return "DEV";
	}

	@Override
	public SocialProfile getProfile(String authorizationCode) {
		if (authorizationCode == null || !CODE_PATTERN.matcher(authorizationCode).matches()) {
			throw new OAuthProviderException(true, new IllegalArgumentException("dev login code must match " + CODE_PATTERN));
		}
		return new SocialProfile(provider(), authorizationCode, null, null);
	}

	@Override
	public String authorizationUri(String state) {
		throw new UnsupportedOperationException("dev provider has no authorization page");
	}
}

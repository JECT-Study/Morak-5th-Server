package com.morak.feature.auth.application.service;

import com.morak.feature.auth.application.port.out.SocialOAuthProvider;
import com.morak.feature.auth.domain.vo.SocialProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SocialOAuthProviderRegistry {

	private final Map<SocialProvider, SocialOAuthProvider> providers;

	public SocialOAuthProviderRegistry(List<SocialOAuthProvider> providers) {
		this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
				SocialOAuthProvider::provider,
				Function.identity()));
	}

	public SocialOAuthProvider get(String provider) {
		// valueOf는 모르는 이름이면 IllegalArgumentException을 던진다.
		SocialOAuthProvider result = providers.get(SocialProvider.valueOf(provider.toUpperCase(Locale.ROOT)));
		if (result == null) {
			throw new IllegalArgumentException("Unsupported social login provider: " + provider);
		}
		return result;
	}
}

package com.morak.feature.auth.application.service;

import com.morak.feature.auth.application.port.out.SocialOAuthProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SocialOAuthProviderRegistry {

	private final Map<String, SocialOAuthProvider> providers;

	public SocialOAuthProviderRegistry(List<SocialOAuthProvider> providers) {
		this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
				provider -> provider.provider().toUpperCase(Locale.ROOT),
				Function.identity()));
	}

	public SocialOAuthProvider get(String provider) {
		SocialOAuthProvider result = providers.get(provider.toUpperCase(Locale.ROOT));
		if (result == null) {
			throw new IllegalArgumentException("Unsupported social login provider: " + provider);
		}
		return result;
	}
}

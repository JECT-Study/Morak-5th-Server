package com.morak.feature.auth.domain.repository;

import com.morak.feature.auth.domain.entity.SocialAccount;
import java.util.Optional;

public interface SocialAccountRepository {

	Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

	SocialAccount save(SocialAccount socialAccount);
}

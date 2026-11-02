package com.morak.feature.auth.infrastructure.persistence;

import com.morak.feature.auth.domain.entity.SocialAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSocialAccountRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}

package com.morak.feature.auth.infrastructure.persistence;

import com.morak.feature.auth.domain.entity.SocialAccount;
import com.morak.feature.auth.domain.repository.SocialAccountRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SocialAccountRepositoryAdapter implements SocialAccountRepository {

	private final JpaSocialAccountRepository jpaRepository;

	public SocialAccountRepositoryAdapter(JpaSocialAccountRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId) {
		return jpaRepository.findByProviderAndProviderUserId(provider, providerUserId);
	}

	@Override
	public SocialAccount save(SocialAccount socialAccount) { return jpaRepository.save(socialAccount); }
}

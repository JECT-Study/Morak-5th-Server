package com.morak.feature.auth.infrastructure.persistence;

import com.morak.feature.auth.domain.entity.SocialAccount;
import com.morak.feature.auth.domain.repository.SocialAccountRepository;
import com.morak.feature.auth.domain.exception.DuplicateSocialAccountException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
	public SocialAccount save(SocialAccount socialAccount) {
		try {
			return jpaRepository.saveAndFlush(socialAccount);
		} catch (DataIntegrityViolationException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof ConstraintViolationException violation
						&& "23505".equals(violation.getSQLState())
						&& "uk_social_account_provider_user".equals(violation.getConstraintName())) {
					throw new DuplicateSocialAccountException(exception);
				}
			}
			throw exception;
		}
	}
}

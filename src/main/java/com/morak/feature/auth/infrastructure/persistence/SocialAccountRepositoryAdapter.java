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
	public void delete(SocialAccount socialAccount) {
		jpaRepository.delete(socialAccount);
		// Hibernate는 DELETE를 INSERT 뒤에 내보내므로, 같은 키로 다시 연결하기 전에 먼저 flush한다.
		jpaRepository.flush();
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

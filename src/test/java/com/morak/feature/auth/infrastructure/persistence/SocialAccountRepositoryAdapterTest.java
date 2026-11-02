package com.morak.feature.auth.infrastructure.persistence;

import com.morak.feature.auth.domain.entity.SocialAccount;
import com.morak.feature.auth.domain.exception.DuplicateSocialAccountException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialAccountRepositoryAdapterTest {

	@Test
	void translatesOnlyTheSocialAccountUniqueViolation() {
		var repository = mock(JpaSocialAccountRepository.class);
		var adapter = new SocialAccountRepositoryAdapter(repository);
		var account = new SocialAccount(1L, "KAKAO", "123");
		for (String constraint : new String[] {"uk_social_account_provider_user", "other_unique", "fk_social_account_user"}) {
			for (String sqlState : new String[] {"23505", "23503"}) {
				var cause = new ConstraintViolationException("constraint failed", new SQLException("failure", sqlState), constraint);
				var failure = new DataIntegrityViolationException("save failed", cause);
				doThrow(failure).when(repository).saveAndFlush(account);
				if (constraint.equals("uk_social_account_provider_user") && sqlState.equals("23505")) {
					assertSame(failure, assertThrows(DuplicateSocialAccountException.class,
							() -> adapter.save(account)).getCause());
				} else {
					assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> adapter.save(account)));
				}
			}
		}
	}
}

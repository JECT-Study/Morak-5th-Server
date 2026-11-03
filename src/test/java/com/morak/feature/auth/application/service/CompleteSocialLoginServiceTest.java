package com.morak.feature.auth.application.service;

import org.mockito.InOrder;
import com.morak.feature.auth.application.dto.result.IssuedTokens;
import com.morak.feature.auth.application.dto.result.AccessToken;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.auth.application.port.out.SocialProfile;
import com.morak.feature.auth.domain.entity.SocialAccount;
import com.morak.feature.auth.domain.exception.DuplicateSocialAccountException;
import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import com.morak.feature.auth.domain.repository.SocialAccountRepository;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CompleteSocialLoginServiceTest {

	private final SocialAccountRepository accounts = mock(SocialAccountRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
	private final ServiceTokenIssuer tokens = mock(ServiceTokenIssuer.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
	private final TransactionStatus first = mock(TransactionStatus.class);
	private final TransactionStatus recovery = mock(TransactionStatus.class);
	private final SocialProfile profile = new SocialProfile("KAKAO", "123", "user@example.com", null);
	private final CompleteSocialLoginService service = new CompleteSocialLoginService(
			accounts, users, refreshTokens, tokens, Duration.ofDays(14), transactions);
	private final DuplicateSocialAccountException conflict = new DuplicateSocialAccountException(new RuntimeException());

	CompleteSocialLoginServiceTest() {
		when(transactions.getTransaction(any())).thenReturn(first, recovery);
		when(users.save(any())).thenReturn(new User(profile.email(), null));
		when(accounts.findByProviderAndProviderUserId("KAKAO", "123")).thenReturn(Optional.empty());
	}

	@Test
	void rollsBackBeforeLookingUpTheWinningAccountInANewTransaction() {
		when(accounts.findByProviderAndProviderUserId("KAKAO", "123"))
				.thenReturn(Optional.empty(), Optional.of(new SocialAccount(42L, "KAKAO", "123")));
		when(accounts.save(any())).thenThrow(conflict);
		User winner = mock(User.class);
		when(winner.getId()).thenReturn(42L);
		when(winner.isActive()).thenReturn(true);
		when(users.findById(42L)).thenReturn(Optional.of(winner));
		when(tokens.createAccessToken(42L, false)).thenReturn(new AccessToken(
				"access", java.time.Instant.parse("2026-11-02T12:30:00Z")));
		when(tokens.createRefreshToken()).thenReturn("refresh");

		IssuedTokens result = service.login(profile);
		assertEquals("access", result.accessToken());
		assertEquals(java.time.Instant.parse("2026-11-02T12:30:00Z"), result.accessTokenExpiresAt());

		InOrder order = inOrder(transactions, accounts, users, tokens);
		order.verify(transactions).getTransaction(argThat(definition ->
				definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
		order.verify(accounts).findByProviderAndProviderUserId("KAKAO", "123");
		order.verify(users).save(any());
		order.verify(accounts).save(any());
		order.verify(transactions).rollback(first);
		order.verify(transactions).getTransaction(argThat(definition ->
				definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
		order.verify(accounts).findByProviderAndProviderUserId("KAKAO", "123");
		order.verify(users).findById(42L);
		order.verify(tokens).createAccessToken(42L, false);
		order.verify(transactions).commit(recovery);
		verify(accounts, times(1)).save(any());
		verify(refreshTokens).save(any());
	}

	@Test
	void propagatesOtherStorageFailuresWithoutRetrying() {
		DataIntegrityViolationException failure = new DataIntegrityViolationException("unrelated constraint");
		when(accounts.save(any())).thenThrow(failure);
		assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.login(profile)));
		verify(transactions).rollback(first);
		verify(transactions, times(1)).getTransaction(any());
		verifyNoInteractions(tokens, refreshTokens);
	}

	@Test
	void preservesConflictIfWinningAccountCannotBeFound() {
		when(accounts.save(any())).thenThrow(conflict);
		assertSame(conflict, assertThrows(DuplicateSocialAccountException.class, () -> service.login(profile)));
		verify(transactions).rollback(first);
		verify(transactions).rollback(recovery);
		verify(accounts, times(1)).save(any());
		verifyNoInteractions(tokens, refreshTokens);
	}
}

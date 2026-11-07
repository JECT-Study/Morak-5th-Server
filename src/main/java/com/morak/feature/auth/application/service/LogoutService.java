package com.morak.feature.auth.application.service;

import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {
	private final RefreshTokenRepository refreshTokens;
	private final ServiceTokenIssuer tokens;

	public LogoutService(RefreshTokenRepository refreshTokens, ServiceTokenIssuer tokens) {
		this.refreshTokens = refreshTokens;
		this.tokens = tokens;
	}

	/**
	 * Revokes only the supplied login token under the same row lock used by rotation.
	 * Unknown, expired and already-revoked tokens are accepted; existing Access Tokens remain valid.
	 */
	@Transactional
	public void logout(String rawToken) {
		refreshTokens.findByTokenHashForUpdate(tokens.hash(rawToken))
				.ifPresent(token -> token.revoke(LocalDateTime.now()));
	}
}

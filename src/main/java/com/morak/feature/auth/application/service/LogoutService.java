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

	/** 회원 탈퇴처럼 한 사용자의 모든 로그인 세션을 끊을 때 쓴다. 이미 폐기된 토큰의 폐기 시각은 유지한다. */
	@Transactional
	public void logoutAll(long userId) {
		refreshTokens.revokeAllByUserId(userId, LocalDateTime.now());
	}
}

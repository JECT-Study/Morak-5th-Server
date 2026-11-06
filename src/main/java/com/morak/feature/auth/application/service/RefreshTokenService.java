package com.morak.feature.auth.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.result.AccessToken;
import com.morak.feature.auth.application.dto.result.IssuedTokens;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.auth.application.port.out.UserAuthPort;
import com.morak.feature.auth.application.port.out.UserAuthPort.UserState;
import com.morak.feature.auth.domain.entity.RefreshToken;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import com.morak.feature.auth.domain.vo.AuthUserId;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
	private final RefreshTokenRepository refreshTokens;
	private final UserAuthPort users;
	private final ServiceTokenIssuer tokens;
	private final Duration refreshValidity;

	public RefreshTokenService(RefreshTokenRepository refreshTokens, UserAuthPort users,
			ServiceTokenIssuer tokens,
			@Value("${morak.auth.jwt.refresh-token-validity}") Duration refreshValidity) {
		if (refreshValidity.isZero() || refreshValidity.isNegative()) {
			throw new IllegalStateException("morak.auth.jwt.refresh-token-validity must be positive");
		}
		this.refreshTokens = refreshTokens;
		this.users = users;
		this.tokens = tokens;
		this.refreshValidity = refreshValidity;
	}

	@Transactional
	public IssuedTokens refresh(String rawToken) {
		RefreshToken previous = refreshTokens.findByTokenHashForUpdate(tokens.hash(rawToken))
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
		// Capture time after acquiring the lock: another request may have held it until expiry.
		LocalDateTime now = LocalDateTime.now();
		if (!previous.isUsableAt(now)) {
			throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		UserState user = users.findUser(new AuthUserId(previous.getUserId()))
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
		if (!user.active()) {
			throw new BusinessException(AuthErrorCode.WITHDRAWN_USER);
		}

		previous.revoke(now);
		AccessToken accessToken = tokens.createAccessToken(user.userId(), user.onboarded());
		String refreshToken = tokens.createRefreshToken();
		refreshTokens.save(new RefreshToken(user.userId().value(), tokens.hash(refreshToken), now.plus(refreshValidity)));
		return new IssuedTokens(accessToken.value(), refreshToken, "Bearer", tokens.accessValiditySeconds(),
				accessToken.expiresAt(), user.onboarded());
	}
}

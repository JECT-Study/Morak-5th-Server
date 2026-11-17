package com.morak.feature.user.infrastructure.auth;

import com.morak.feature.auth.application.service.LogoutService;
import com.morak.feature.user.application.port.out.LoginSessionRevoker;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
class LoginSessionRevokerAdapter implements LoginSessionRevoker {

	private final LogoutService logoutService;

	LoginSessionRevokerAdapter(LogoutService logoutService) {
		this.logoutService = logoutService;
	}

	@Override
	public void revokeAll(long userId, LocalDateTime now) {
		logoutService.logoutAll(userId, now);
	}
}

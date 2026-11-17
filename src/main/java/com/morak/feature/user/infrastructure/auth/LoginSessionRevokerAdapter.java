package com.morak.feature.user.infrastructure.auth;

import com.morak.feature.auth.application.service.LogoutService;
import com.morak.feature.user.application.port.out.LoginSessionRevoker;
import org.springframework.stereotype.Component;

@Component
class LoginSessionRevokerAdapter implements LoginSessionRevoker {

	private final LogoutService logoutService;

	LoginSessionRevokerAdapter(LogoutService logoutService) {
		this.logoutService = logoutService;
	}

	@Override
	public void revokeAll(long userId) {
		logoutService.logoutAll(userId);
	}
}

package com.morak.feature.auth.application.service;

import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.application.exception.InvalidLoginUserException;
import com.morak.feature.auth.application.port.out.UserAuthPort;
import com.morak.feature.auth.application.port.out.UserAuthPort.UserState;
import com.morak.feature.auth.domain.vo.AuthUserId;
import org.springframework.stereotype.Service;

@Service
public class AuthenticateUserService {
	private final UserAuthPort userAuthPort;

	public AuthenticateUserService(UserAuthPort userAuthPort) {
		this.userAuthPort = userAuthPort;
	}

	public LoginUser authenticate(AuthUserId userId) {
		UserState user = userAuthPort.findUser(userId).filter(UserState::active)
				.orElseThrow(InvalidLoginUserException::new);
		return new LoginUser(user.userId(), user.onboarded());
	}
}

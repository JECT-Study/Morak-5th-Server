package com.morak.feature.auth.application.service;

import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.application.exception.InvalidLoginUserException;
import com.morak.feature.user.application.dto.result.UserAuthInfo;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.domain.vo.UserId;
import org.springframework.stereotype.Service;

@Service
public class AuthenticateUserService {
	private final UserService userService;

	public AuthenticateUserService(UserService userService) {
		this.userService = userService;
	}

	public LoginUser authenticate(UserId userId) {
		UserAuthInfo user = userService.findUser(userId).filter(UserAuthInfo::active)
				.orElseThrow(InvalidLoginUserException::new);
		return new LoginUser(user.userId(), user.onboarded());
	}
}

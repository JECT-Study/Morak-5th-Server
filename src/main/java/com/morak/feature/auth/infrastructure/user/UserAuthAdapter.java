package com.morak.feature.auth.infrastructure.user;

import com.morak.feature.auth.application.port.out.UserAuthPort;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.user.application.dto.result.UserAuthInfo;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.domain.vo.UserId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserAuthAdapter implements UserAuthPort {
	private final UserService userService;

	public UserAuthAdapter(UserService userService) {
		this.userService = userService;
	}

	@Override
	public Optional<UserState> findUser(AuthUserId userId) {
		return userService.findUser(new UserId(userId.value())).map(UserAuthAdapter::toUserState);
	}

	@Override
	public UserState createUser(String email, String profileImageUrl) {
		return toUserState(userService.createUser(email, profileImageUrl));
	}

	@Override
	public Optional<UserState> updateSocialProfile(AuthUserId userId, String email, String profileImageUrl) {
		return userService.updateSocialProfile(new UserId(userId.value()), email, profileImageUrl).map(UserAuthAdapter::toUserState);
	}

	private static UserState toUserState(UserAuthInfo user) {
		return new UserState(new AuthUserId(user.userId().value()), user.active(), user.onboarded());
	}
}

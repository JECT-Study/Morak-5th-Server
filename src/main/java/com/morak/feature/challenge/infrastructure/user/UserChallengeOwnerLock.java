package com.morak.feature.challenge.infrastructure.user;

import com.morak.feature.challenge.application.port.out.ChallengeOwnerLock;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.domain.vo.UserId;
import org.springframework.stereotype.Component;

@Component
class UserChallengeOwnerLock implements ChallengeOwnerLock {

	private final UserService users;

	UserChallengeOwnerLock(UserService users) {
		this.users = users;
	}

	@Override
	public void lock(long userId) {
		users.lockActiveUser(new UserId(userId));
	}
}

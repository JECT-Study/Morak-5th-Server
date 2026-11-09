package com.morak.feature.challenge.infrastructure.user;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.port.out.ChallengeOwnerLock;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.user.domain.vo.UserId;
import org.springframework.stereotype.Component;

@Component
class UserChallengeOwnerLock implements ChallengeOwnerLock {

	private final UserRepository users;

	UserChallengeOwnerLock(UserRepository users) {
		this.users = users;
	}

	@Override
	public void lock(long userId) {
		users.findByIdForUpdate(new UserId(userId))
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
	}
}

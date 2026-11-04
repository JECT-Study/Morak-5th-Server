package com.morak.feature.user.application.dto.result;

import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.vo.UserId;

public record UserAuthInfo(UserId userId, boolean active, boolean onboarded) {
	public static UserAuthInfo from(User user) {
		return new UserAuthInfo(new UserId(user.getId()), user.isActive(), user.isOnboarded());
	}
}

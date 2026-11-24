package com.morak.feature.auth.application.dto.result;

import com.morak.feature.auth.domain.vo.AuthUserId;
import java.security.Principal;

/** @param admin 운영진(users.role=ADMIN) 여부. /admin/** 접근 판단에 쓴다 */
public record LoginUser(AuthUserId userId, boolean onboarded, boolean admin) implements Principal {

	public LoginUser(AuthUserId userId, boolean onboarded) {
		this(userId, onboarded, false);
	}

	@Override
	public String getName() {
		return Long.toString(userId.value());
	}
}

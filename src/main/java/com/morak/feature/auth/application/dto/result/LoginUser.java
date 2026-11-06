package com.morak.feature.auth.application.dto.result;

import com.morak.feature.auth.domain.vo.AuthUserId;
import java.security.Principal;

public record LoginUser(AuthUserId userId, boolean onboarded) implements Principal {

	@Override
	public String getName() {
		return Long.toString(userId.value());
	}
}

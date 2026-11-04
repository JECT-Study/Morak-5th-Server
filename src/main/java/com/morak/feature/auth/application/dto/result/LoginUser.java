package com.morak.feature.auth.application.dto.result;

import com.morak.feature.user.domain.vo.UserId;
import java.security.Principal;

public record LoginUser(UserId userId, boolean onboarded) implements Principal {

	@Override
	public String getName() {
		return userId.value().toString();
	}
}

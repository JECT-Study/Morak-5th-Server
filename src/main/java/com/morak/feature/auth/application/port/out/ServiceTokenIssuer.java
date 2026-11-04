package com.morak.feature.auth.application.port.out;

import com.morak.feature.user.domain.vo.UserId;
import com.morak.feature.auth.application.dto.result.AccessToken;

public interface ServiceTokenIssuer {

	AccessToken createAccessToken(UserId userId, boolean onboarded);

	String createRefreshToken();

	String hash(String token);

	long accessValiditySeconds();
}

package com.morak.feature.auth.application.port.out;

import com.morak.feature.auth.application.dto.result.AccessToken;
import com.morak.feature.auth.domain.vo.AuthUserId;

public interface ServiceTokenIssuer {

	AccessToken createAccessToken(AuthUserId userId, boolean onboarded);

	String createRefreshToken();

	String hash(String token);

	long accessValiditySeconds();
}

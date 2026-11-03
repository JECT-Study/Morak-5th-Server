package com.morak.feature.auth.application.port.out;

import com.morak.feature.auth.application.dto.result.AccessToken;

public interface ServiceTokenIssuer {

	AccessToken createAccessToken(Long userId, boolean onboarded);

	String createRefreshToken();

	String hash(String token);

	long accessValiditySeconds();
}

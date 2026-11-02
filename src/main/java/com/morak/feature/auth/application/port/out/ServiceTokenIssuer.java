package com.morak.feature.auth.application.port.out;

public interface ServiceTokenIssuer {

	String createAccessToken(Long userId, boolean onboarded);

	String createRefreshToken();

	String hash(String token);

	long accessValiditySeconds();
}

package com.morak.feature.auth.application.port.out;

import com.morak.feature.auth.domain.vo.SocialProvider;

public interface SocialOAuthProvider {

	SocialProvider provider();

	String authorizationUri(String state);

	SocialProfile getProfile(String authorizationCode);
}

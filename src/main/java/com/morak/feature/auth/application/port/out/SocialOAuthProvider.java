package com.morak.feature.auth.application.port.out;

public interface SocialOAuthProvider {

	String provider();

	String authorizationUri(String state);

	SocialProfile getProfile(String authorizationCode);
}

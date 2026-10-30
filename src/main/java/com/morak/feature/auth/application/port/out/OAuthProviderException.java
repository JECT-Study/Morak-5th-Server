package com.morak.feature.auth.application.port.out;

public class OAuthProviderException extends RuntimeException {

	private final boolean invalidAuthorization;

	public OAuthProviderException(boolean invalidAuthorization, Throwable cause) {
		super(cause);
		this.invalidAuthorization = invalidAuthorization;
	}

	public boolean isInvalidAuthorization() {
		return invalidAuthorization;
	}
}

package com.morak.feature.auth.application.port.out;

import lombok.Getter;

@Getter
public class OAuthProviderException extends RuntimeException {

	private final boolean invalidAuthorization;

	public OAuthProviderException(boolean invalidAuthorization, Throwable cause) {
		super(cause);
		this.invalidAuthorization = invalidAuthorization;
	}

}

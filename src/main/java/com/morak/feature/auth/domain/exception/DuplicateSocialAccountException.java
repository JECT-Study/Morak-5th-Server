package com.morak.feature.auth.domain.exception;

public class DuplicateSocialAccountException extends RuntimeException {

	public DuplicateSocialAccountException(Throwable cause) {
		super("Social account already exists", cause);
	}
}

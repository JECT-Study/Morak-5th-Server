package com.morak.feature.auth.application.exception;

public class InvalidLoginUserException extends RuntimeException {
	public InvalidLoginUserException() {
		super("No active user exists for the supplied identity");
	}
}

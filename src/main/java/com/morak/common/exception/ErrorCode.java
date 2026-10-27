package com.morak.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

	String code();

	String message();

	HttpStatus status();
}

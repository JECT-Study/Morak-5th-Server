package com.morak.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void preservesClientErrorStatuses() {
		for (HttpStatus status : new HttpStatus[] {
			HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.UNSUPPORTED_MEDIA_TYPE
		}) {
			var response = handler.handleUnexpectedException(new ResponseStatusException(status));
			assertEquals(status, response.getStatusCode());
			assertFalse(response.getBody().success());
			assertEquals(status == HttpStatus.NOT_FOUND ? "COMMON_002" : "COMMON_001",
					response.getBody().error().code());
		}
	}

	@Test
	void preservesMethodNotAllowedHeader() {
		var response = handler.handleUnexpectedException(
				new HttpRequestMethodNotSupportedException("POST", java.util.List.of("GET")));
		assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
		assertEquals("GET", response.getHeaders().getFirst("Allow"));
	}

	@Test
	void unexpectedErrorsRemainInternalServerErrors() {
		var response = handler.handleUnexpectedException(new RuntimeException("private detail"));
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals("COMMON_003", response.getBody().error().code());
		assertEquals(CommonErrorCode.INTERNAL_SERVER_ERROR.message(), response.getBody().error().message());
	}
}

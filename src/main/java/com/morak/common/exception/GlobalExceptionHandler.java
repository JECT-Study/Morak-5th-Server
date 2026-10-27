package com.morak.common.exception;

import com.morak.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(errorCode.status())
				.body(ApiResponse.failure(errorCode.code(), errorCode.message()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception) {
		FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
		String message =
				fieldError == null || fieldError.getDefaultMessage() == null
						? CommonErrorCode.INVALID_REQUEST.message()
						: fieldError.getDefaultMessage();
		return badRequest(message);
	}

	@ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
	public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
		return badRequest(CommonErrorCode.INVALID_REQUEST.message());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		if (exception instanceof ErrorResponse errorResponse) {
			var status = errorResponse.getStatusCode();
			if (status.is5xxServerError()) {
				log.error("Unhandled request error", exception);
			}
			ErrorCode errorCode =
					status.value() == 404
							? CommonErrorCode.RESOURCE_NOT_FOUND
							: status.is5xxServerError()
									? CommonErrorCode.INTERNAL_SERVER_ERROR
									: CommonErrorCode.INVALID_REQUEST;
			return ResponseEntity.status(status)
					.headers(errorResponse.getHeaders())
					.body(ApiResponse.failure(errorCode.code(), errorCode.message()));
		}

		log.error("Unhandled request error", exception);
		ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(errorCode.status())
				.body(ApiResponse.failure(errorCode.code(), errorCode.message()));
	}

	private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
		ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
		return ResponseEntity.status(errorCode.status()).body(ApiResponse.failure(errorCode.code(), message));
	}
}

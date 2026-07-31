package com.smartai.core.platform.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.smartai.core.platform.web.RequestContext;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
final class GlobalApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiProblem> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		List<ApiErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
			.limit(100)
			.map(error -> new ApiErrorDetail(
				error.getCode() == null ? "INVALID" : error.getCode(),
				error.getField(),
				error.getDefaultMessage()))
			.toList();
		return problem(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiProblem> handleUnreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
		return problem(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request body is invalid", List.of());
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ApiProblem> handleMissingHeader(
			MissingRequestHeaderException exception,
			HttpServletRequest request) {
		return problem(
			request,
			HttpStatus.BAD_REQUEST,
			"VALIDATION_FAILED",
			"Required request header is missing",
			List.of(new ApiErrorDetail("REQUIRED", exception.getHeaderName(), "Header is required")));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiProblem> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception,
			HttpServletRequest request) {
		return problem(
			request,
			HttpStatus.BAD_REQUEST,
			"VALIDATION_FAILED",
			"Request parameter has an invalid type",
			List.of(new ApiErrorDetail("INVALID", exception.getName(), "Value has an invalid format")));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiProblem> handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
		return problem(request, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found", List.of());
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiProblem> handleApiException(ApiException exception, HttpServletRequest request) {
		return problem(request, exception.status(), exception.code(), exception.getMessage(), exception.details());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiProblem> handleUnexpected(Exception exception, HttpServletRequest request) {
		LOGGER.error("Unhandled API error for traceId={}", RequestContext.traceId(request), exception);
		return problem(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", List.of());
	}

	private static ResponseEntity<ApiProblem> problem(
			HttpServletRequest request,
			HttpStatus status,
			String code,
			String message,
			List<ApiErrorDetail> details) {
		ApiProblem problem = ApiProblem.of(
			RequestContext.requestId(request),
			RequestContext.traceId(request),
			code,
			message,
			details);
		return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(problem);
	}
}

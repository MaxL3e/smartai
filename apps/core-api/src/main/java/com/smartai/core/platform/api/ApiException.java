package com.smartai.core.platform.api;

import java.util.List;

import org.springframework.http.HttpStatus;

public final class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final List<ApiErrorDetail> details;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, List.of());
	}

	public ApiException(HttpStatus status, String code, String message, List<ApiErrorDetail> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = List.copyOf(details);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public List<ApiErrorDetail> details() {
		return details;
	}
}

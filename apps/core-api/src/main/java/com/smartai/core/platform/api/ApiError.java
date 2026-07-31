package com.smartai.core.platform.api;

import java.util.List;

public record ApiError(
		String code,
		String message,
		boolean retryable,
		Integer retryAfterSeconds,
		String traceId,
		List<ApiErrorDetail> details) {

	public ApiError {
		if (retryAfterSeconds != null && retryAfterSeconds < 0) {
			throw new IllegalArgumentException("retryAfterSeconds must not be negative");
		}
		if (!retryable && retryAfterSeconds != null) {
			throw new IllegalArgumentException("retryAfterSeconds requires a retryable error");
		}
		details = details == null ? List.of() : List.copyOf(details);
	}
}

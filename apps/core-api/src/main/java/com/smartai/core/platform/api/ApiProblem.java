package com.smartai.core.platform.api;

import java.util.List;
import java.util.UUID;

public record ApiProblem(UUID requestId, ApiError error) {

	public static ApiProblem of(UUID requestId, String traceId, String code, String message) {
		return of(requestId, traceId, code, message, List.of());
	}

	public static ApiProblem of(
			UUID requestId,
			String traceId,
			String code,
			String message,
			List<ApiErrorDetail> details) {
		return of(requestId, traceId, code, message, false, null, details);
	}

	public static ApiProblem of(
			UUID requestId,
			String traceId,
			String code,
			String message,
			boolean retryable,
			Integer retryAfterSeconds,
			List<ApiErrorDetail> details) {
		return new ApiProblem(
			requestId,
			new ApiError(code, message, retryable, retryAfterSeconds, traceId, details));
	}
}

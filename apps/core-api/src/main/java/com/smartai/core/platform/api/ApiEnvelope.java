package com.smartai.core.platform.api;

import java.util.UUID;

public record ApiEnvelope<T, M extends ResponseMeta>(UUID requestId, T data, M meta) {

	public static <T> ApiEnvelope<T, ApiMeta> success(UUID requestId, String traceId, T data) {
		return new ApiEnvelope<>(requestId, data, ApiMeta.now(traceId));
	}

	public static <T> ApiEnvelope<T, PageMeta> page(
			UUID requestId,
			String traceId,
			T data,
			int limit,
			boolean hasMore,
			String nextCursor) {
		return new ApiEnvelope<>(requestId, data, PageMeta.now(traceId, limit, hasMore, nextCursor));
	}
}

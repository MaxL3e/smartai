package com.smartai.core.platform.api;

import java.time.Instant;

public record PageMeta(
		Instant servedAt,
		String traceId,
		int limit,
		boolean hasMore,
		String nextCursor) implements ResponseMeta {

	public PageMeta {
		if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
	}

	public static PageMeta now(String traceId, int limit, boolean hasMore, String nextCursor) {
		return new PageMeta(Instant.now(), traceId, limit, hasMore, nextCursor);
	}
}

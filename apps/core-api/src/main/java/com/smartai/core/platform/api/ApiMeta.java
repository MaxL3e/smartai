package com.smartai.core.platform.api;

import java.time.Instant;

public record ApiMeta(Instant servedAt, String traceId) implements ResponseMeta {

	public static ApiMeta now(String traceId) {
		return new ApiMeta(Instant.now(), traceId);
	}
}

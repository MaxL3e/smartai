package com.smartai.core.platform.api;

import java.util.UUID;

import com.smartai.core.platform.web.RequestContext;

import jakarta.servlet.http.HttpServletRequest;

public record ApiRequestContext(UUID requestId, String traceId) {

	public static ApiRequestContext from(HttpServletRequest request) {
		return new ApiRequestContext(RequestContext.requestId(request), RequestContext.traceId(request));
	}
}

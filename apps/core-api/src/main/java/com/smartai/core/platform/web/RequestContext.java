package com.smartai.core.platform.web;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestContext {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
	static final String REQUEST_ID_ATTRIBUTE = RequestContext.class.getName() + ".requestId";
	static final String CORRELATION_ID_ATTRIBUTE = RequestContext.class.getName() + ".correlationId";
	private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

	private RequestContext() {
	}

	public static UUID requestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
		return requestId instanceof UUID value ? value : UUID.randomUUID();
	}

	public static String correlationId(HttpServletRequest request) {
		Object correlationId = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
		return correlationId instanceof String value ? value : requestId(request).toString();
	}

	public static String traceId(HttpServletRequest request) {
		String correlationId = correlationId(request);
		return SAFE_TRACE_ID.matcher(correlationId).matches() ? correlationId : requestId(request).toString();
	}
}

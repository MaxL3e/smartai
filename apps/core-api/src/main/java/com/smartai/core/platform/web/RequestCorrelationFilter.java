package com.smartai.core.platform.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class RequestCorrelationFilter extends OncePerRequestFilter {

	private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		UUID requestId = requestId(request.getHeader(RequestContext.REQUEST_ID_HEADER));
		String correlationId = correlationId(request.getHeader(RequestContext.CORRELATION_ID_HEADER), requestId);
		request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, requestId);
		request.setAttribute(RequestContext.CORRELATION_ID_ATTRIBUTE, correlationId);
		response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId.toString());
		response.setHeader(RequestContext.CORRELATION_ID_HEADER, correlationId);
		MDC.put("requestId", requestId.toString());
		MDC.put("correlationId", correlationId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove("requestId");
			MDC.remove("correlationId");
		}
	}

	private static UUID requestId(String candidate) {
		try {
			return candidate == null ? UUID.randomUUID() : UUID.fromString(candidate.trim());
		}
		catch (IllegalArgumentException ignored) {
			return UUID.randomUUID();
		}
	}

	private static String correlationId(String candidate, UUID requestId) {
		if (candidate == null || !SAFE_CORRELATION_ID.matcher(candidate).matches()) return requestId.toString();
		return candidate;
	}
}

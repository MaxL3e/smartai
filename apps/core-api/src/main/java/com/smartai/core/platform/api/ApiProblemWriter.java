package com.smartai.core.platform.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.smartai.core.platform.web.RequestContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public final class ApiProblemWriter {

	private final ObjectMapper objectMapper;

	public ApiProblemWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(
			HttpServletRequest request,
			HttpServletResponse response,
			int status,
			String code,
			String message) throws IOException {
		write(request, response, status, code, message, false, null);
	}

	public void write(
			HttpServletRequest request,
			HttpServletResponse response,
			int status,
			String code,
			String message,
			boolean retryable,
			Integer retryAfterSeconds) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		if (retryAfterSeconds != null) {
			response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString());
		}
		objectMapper.writeValue(
			response.getOutputStream(),
			ApiProblem.of(
				RequestContext.requestId(request),
				RequestContext.traceId(request),
				code,
				message,
				retryable,
				retryAfterSeconds,
				List.of()));
	}
}

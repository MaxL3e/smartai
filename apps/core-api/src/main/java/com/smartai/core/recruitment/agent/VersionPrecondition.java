package com.smartai.core.recruitment.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import com.smartai.core.platform.api.ApiException;

final class VersionPrecondition {

	private static final Pattern ETAG = Pattern.compile("^(?:W/)?\\\"([1-9][0-9]*)\\\"$");

	private VersionPrecondition() {
	}

	static long require(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			throw new ApiException(
				HttpStatus.PRECONDITION_REQUIRED,
				"PRECONDITION_REQUIRED",
				"If-Match is required");
		}
		Matcher matcher = ETAG.matcher(ifMatch.strip());
		if (!matcher.matches()) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED",
				"If-Match must be a quoted positive resource version");
		}
		try {
			return Long.parseLong(matcher.group(1));
		}
		catch (NumberFormatException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "If-Match version is too large");
		}
	}

	static void verify(long expectedVersion, long actualVersion) {
		if (expectedVersion != actualVersion) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"VERSION_CONFLICT",
				"Resource version does not match If-Match");
		}
	}
}

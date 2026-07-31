package com.smartai.core.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiErrorTests {

	@Test
	void acceptsZeroRetryDelayForRetryableErrors() {
		ApiError error = new ApiError(
			"DEPENDENCY_UNAVAILABLE",
			"Retry immediately",
			true,
			0,
			"trace-1",
			null);

		assertThat(error.retryAfterSeconds()).isZero();
		assertThat(error.details()).isEmpty();
	}
}

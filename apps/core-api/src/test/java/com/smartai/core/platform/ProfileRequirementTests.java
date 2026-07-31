package com.smartai.core.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProfileRequirementTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(ProfileGuardConfiguration.class);

	@Test
	void failsFastWhenNoSupportedProfileIsActive() {
		contextRunner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
				.hasRootCauseInstanceOf(IllegalStateException.class)
				.hasStackTraceContaining("An explicit local or production Spring profile is required");
		});
	}

	@Test
	void acceptsAnExplicitSupportedProfile() {
		contextRunner
			.withPropertyValues("spring.profiles.active=local")
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void rejectsLocalThenProductionProfiles() {
		assertMutuallyExclusiveProfilesFail("local,production");
	}

	@Test
	void rejectsProductionThenLocalProfiles() {
		assertMutuallyExclusiveProfilesFail("production,local");
	}

	private void assertMutuallyExclusiveProfilesFail(String profiles) {
		contextRunner
			.withPropertyValues("spring.profiles.active=" + profiles)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasRootCauseInstanceOf(IllegalStateException.class)
					.hasStackTraceContaining("The local and production Spring profiles are mutually exclusive");
			});
	}
}

package com.smartai.core.platform;

import java.util.Arrays;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class ProfileGuardConfiguration {

	ProfileGuardConfiguration(Environment environment) {
		var activeProfiles = Arrays.asList(environment.getActiveProfiles());
		boolean localActive = activeProfiles.contains("local");
		boolean productionActive = activeProfiles.contains("production");

		if (!localActive && !productionActive) {
			throw new IllegalStateException("An explicit local or production Spring profile is required");
		}
		if (localActive && productionActive) {
			throw new IllegalStateException("The local and production Spring profiles are mutually exclusive");
		}
	}
}

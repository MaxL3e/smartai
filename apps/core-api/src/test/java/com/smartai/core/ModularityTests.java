package com.smartai.core;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	void verifiesModuleBoundaries() {
		ApplicationModules.of(CoreApiApplication.class).verify();
	}
}

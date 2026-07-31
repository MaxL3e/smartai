package com.smartai.core.recruitment.agent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.smartai.core.platform.api.ApiException;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;

import jakarta.servlet.http.HttpServletRequest;

@Component
final class TenantActorResolver {

	static final UUID DEMO_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
	static final String TENANT_HEADER = "X-SmartAI-Demo-Tenant-Id";
	static final String USER_HEADER = "X-SmartAI-Demo-User-Id";

	private final Environment environment;

	TenantActorResolver(Environment environment) {
		this.environment = environment;
	}

	TenantActor resolve(HttpServletRequest request) {
		if (!environment.acceptsProfiles(Profiles.of("local"))) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_FAILED", "Authentication is required");
		}
		UUID tenantId = uuidHeader(request, TENANT_HEADER, DEMO_TENANT_ID);
		UUID userId = uuidHeader(request, USER_HEADER, DEMO_USER_ID);
		String displayName = request.getHeader("X-SmartAI-Demo-User-Name");
		if (displayName == null || displayName.isBlank()) displayName = "演示 HR";
		if (displayName.length() > 120) displayName = displayName.substring(0, 120);
		return new TenantActor(tenantId, userId, displayName);
	}

	private static UUID uuidHeader(HttpServletRequest request, String name, UUID fallback) {
		String value = request.getHeader(name);
		if (value == null || value.isBlank()) return fallback;
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", name + " must be a UUID");
		}
	}

	static String tenantKey(UUID tenantId) {
		return "demo-" + UUID.nameUUIDFromBytes(tenantId.toString().getBytes(StandardCharsets.UTF_8));
	}
}

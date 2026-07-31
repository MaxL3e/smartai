package com.smartai.core.recruitment.agent;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
final class LocalDemoTenantInitializer {

	LocalDemoTenantInitializer(JdbcTemplate jdbcTemplate) {
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) "
				+ "SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE tenant_id = ?)",
			TenantActorResolver.DEMO_TENANT_ID,
			TenantActorResolver.tenantKey(TenantActorResolver.DEMO_TENANT_ID),
			"SmartAI 演示租户",
			"ACTIVE",
			TenantActorResolver.DEMO_TENANT_ID);
	}
}

package com.smartai.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class DatabaseMigrationTests {

	private static final List<String> BASE_TABLES = List.of(
		"tenant",
		"external_identity",
		"embed_client",
		"embed_session",
		"requirement_draft",
		"recruitment_task",
		"position_plan_version",
		"position_plan_command",
		"candidate",
		"resume_version",
		"candidate_input_command",
		"task_candidate",
		"match_run",
		"match_result",
		"candidate_list_preview",
		"candidate_list_version",
		"recommendation_report_version",
		"agent_run",
		"human_checkpoint",
		"knowledge_document",
		"knowledge_version",
		"knowledge_chunk",
		"knowledge_upload_session",
		"knowledge_command",
		"resume_document",
		"resume_file_version",
		"resume_file_command",
		"audit_event",
		"outbox_event");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void createsPlatformTablesWithTenantVersionAndAuditColumns() {
		for (String table : BASE_TABLES) {
			Integer tableCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
				Integer.class,
				table);
			assertThat(tableCount).as("table %s", table).isEqualTo(1);

			Integer columnCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.columns "
					+ "WHERE table_schema = 'public' AND table_name = ? "
					+ "AND column_name IN ('tenant_id', 'version', 'created_at', 'created_by', 'updated_at', 'updated_by')",
				Integer.class,
				table);
			assertThat(columnCount).as("baseline columns for %s", table).isEqualTo(6);
		}

		Integer auditSequenceColumn = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM information_schema.columns "
				+ "WHERE table_schema = 'public' AND table_name = 'audit_event' AND column_name = 'tenant_sequence'",
			Integer.class);
		assertThat(auditSequenceColumn).isEqualTo(1);

		Integer auditChainConstraints = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM information_schema.table_constraints "
				+ "WHERE table_schema = 'public' AND table_name = 'audit_event' "
				+ "AND constraint_name IN ('uq_audit_event_tenant_sequence', 'uq_audit_event_tenant_hash')",
			Integer.class);
		assertThat(auditChainConstraints).isEqualTo(2);
	}

	@Test
	void rejectsEmbedSessionClientOwnedByAnotherTenant() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		UUID clientB = UUID.randomUUID();
		UUID identityA = UUID.randomUUID();

		insertTenant(tenantA, "Tenant A");
		insertTenant(tenantB, "Tenant B");
		insertClient(clientB, tenantB);
		insertIdentity(identityA, tenantA);

		assertEmbedSessionRejected(tenantA, clientB, identityA);
	}

	@Test
	void rejectsEmbedSessionIdentityOwnedByAnotherTenant() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		UUID clientA = UUID.randomUUID();
		UUID identityB = UUID.randomUUID();

		insertTenant(tenantA, "Tenant A");
		insertTenant(tenantB, "Tenant B");
		insertClient(clientA, tenantA);
		insertIdentity(identityB, tenantB);

		assertEmbedSessionRejected(tenantA, clientA, identityB);
	}

	private void insertTenant(UUID tenantId, String displayName) {
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, ?)",
			tenantId, "tenant-" + tenantId, displayName, "ACTIVE");
	}

	private void insertClient(UUID clientId, UUID tenantId) {
		jdbcTemplate.update(
			"INSERT INTO embed_client "
				+ "(embed_client_id, tenant_id, client_key, display_name, allowed_origins, status) "
				+ "VALUES (?, ?, ?, ?, ?, ?)",
			clientId, tenantId, "client-" + clientId, "Client", "https://example.test", "ACTIVE");
	}

	private void insertIdentity(UUID identityId, UUID tenantId) {
		jdbcTemplate.update(
			"INSERT INTO external_identity "
				+ "(external_identity_id, tenant_id, provider, subject, status) VALUES (?, ?, ?, ?, ?)",
			identityId, tenantId, "oidc", "subject-" + identityId, "ACTIVE");
	}

	private void assertEmbedSessionRejected(UUID tenantId, UUID clientId, UUID identityId) {
		assertThatThrownBy(() -> jdbcTemplate.update(
			"INSERT INTO embed_session "
				+ "(embed_session_id, tenant_id, embed_client_id, external_identity_id, bootstrap_token_hash, "
				+ "protocol_version, parent_origin, context_hash, status, expires_at) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			UUID.randomUUID(), tenantId, clientId, identityId,
			UUID.randomUUID().toString().replace("-", "").repeat(2), "1.0",
			"https://example.test", "b".repeat(64), "ACTIVE", OffsetDateTime.now().plusMinutes(5)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}

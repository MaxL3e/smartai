package com.smartai.core.recruitment.agent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartai.core.recruitment.agent.PositionPlanModels.AgentRun;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanCheckpoint;
import com.smartai.core.recruitment.agent.PositionPlanModels.PositionPlanVersion;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Task;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

@Repository
public class PositionPlanRepository {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final PositionPlanHasher hasher;

	public PositionPlanRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, PositionPlanHasher hasher) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.hasher = hasher;
	}

	Optional<Task> findTask(UUID tenantId, UUID taskId) {
		return jdbcTemplate.query(
			"SELECT task_json FROM recruitment_task WHERE tenant_id = ? AND recruitment_task_id = ?",
			(resultSet, rowNum) -> read(resultSet.getString("task_json"), Task.class),
			tenantId,
			taskId).stream().findFirst();
	}

	Optional<UUID> findSourceDraftId(UUID tenantId, UUID taskId) {
		return jdbcTemplate.query(
			"SELECT source_draft_id FROM recruitment_task WHERE tenant_id = ? AND recruitment_task_id = ?",
			(resultSet, rowNum) -> resultSet.getObject("source_draft_id", UUID.class),
			tenantId,
			taskId).stream().findFirst();
	}

	int updateTask(UUID tenantId, Task previous, Task updated, String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE recruitment_task SET task_json = ?, version = ?, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND recruitment_task_id = ? AND version = ?",
			json(updated), updated.version(), updated.updatedAt(), updatedBy,
			tenantId, previous.id(), previous.version());
	}

	int nextPlanVersionNo(UUID tenantId, UUID taskId) {
		Integer current = jdbcTemplate.queryForObject(
			"SELECT COALESCE(MAX(version_no), 0) FROM position_plan_version "
				+ "WHERE tenant_id = ? AND recruitment_task_id = ?",
			Integer.class,
			tenantId,
			taskId);
		return current == null ? 1 : current + 1;
	}

	void insertPlan(UUID tenantId, PositionPlanVersion plan, String generatorKind, String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO position_plan_version (position_plan_version_id, tenant_id, recruitment_task_id, "
				+ "version_no, status, plan_json, content_hash, based_on_run_id, generator_kind, version, "
				+ "created_at, created_by, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			plan.id(), tenantId, plan.taskId(), plan.versionNo(), plan.status(), json(plan), plan.contentHash(),
			plan.basedOnRunId(), generatorKind, plan.version(), plan.createdAt(), createdBy, plan.updatedAt(), createdBy);
	}

	Optional<PositionPlanVersion> findPlan(UUID tenantId, UUID planId) {
		return jdbcTemplate.query(
			"SELECT plan_json FROM position_plan_version WHERE tenant_id = ? AND position_plan_version_id = ?",
			(resultSet, rowNum) -> read(resultSet.getString("plan_json"), PositionPlanVersion.class),
			tenantId,
			planId).stream().findFirst();
	}

	Optional<PositionPlanVersion> findCurrentPlan(UUID tenantId, UUID taskId) {
		return jdbcTemplate.query(
			"SELECT plan_json FROM position_plan_version WHERE tenant_id = ? AND recruitment_task_id = ? "
				+ "ORDER BY version_no DESC LIMIT 1",
			(resultSet, rowNum) -> read(resultSet.getString("plan_json"), PositionPlanVersion.class),
			tenantId,
			taskId).stream().findFirst();
	}

	int updatePlan(UUID tenantId, PositionPlanVersion previous, PositionPlanVersion updated, String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE position_plan_version SET status = ?, plan_json = ?, content_hash = ?, version = ?, "
				+ "approved_by_user_id = ?, approved_by_display_name = ?, approved_at = ?, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND position_plan_version_id = ? AND version = ?",
			updated.status(), json(updated), updated.contentHash(), updated.version(),
			updated.approvedBy() == null ? null : updated.approvedBy().id(),
			updated.approvedBy() == null ? null : updated.approvedBy().displayName(),
			updated.approvedAt(), updated.updatedAt(), updatedBy,
			tenantId, previous.id(), previous.version());
	}

	void insertAgentRun(UUID tenantId, AgentRun run, String generatorKind, String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO agent_run (agent_run_id, tenant_id, recruitment_task_id, run_type, status, "
				+ "workflow_version, trace_id, generator_kind, failure_code, started_at, finished_at, version, "
				+ "created_at, created_by, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)",
			run.id(), tenantId, run.taskId(), run.runType(), run.status(), run.workflowVersion(), run.traceId(),
			generatorKind, run.failureCode(), run.startedAt(), run.finishedAt(), run.createdAt(), createdBy,
			run.createdAt(), createdBy);
	}

	void updateAgentRunStatus(
			UUID tenantId,
			UUID runId,
			String status,
			UUID resultResourceId,
			OffsetDateTime finishedAt,
			OffsetDateTime updatedAt,
			String updatedBy) {
		jdbcTemplate.update(
			"UPDATE agent_run SET status = ?, result_resource_id = COALESCE(?, result_resource_id), "
				+ "finished_at = ?, version = version + 1, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND agent_run_id = ?",
			status, resultResourceId, finishedAt, updatedAt, updatedBy, tenantId, runId);
	}

	Optional<AgentRun> findAgentRun(UUID tenantId, UUID runId) {
		return jdbcTemplate.query(
			"SELECT agent_run_id, recruitment_task_id, run_type, status, workflow_version, trace_id, "
				+ "failure_code, created_at, started_at, finished_at FROM agent_run "
				+ "WHERE tenant_id = ? AND agent_run_id = ?",
			(resultSet, rowNum) -> mapAgentRun(resultSet),
			tenantId,
			runId).stream().findFirst();
	}

	void insertCheckpoint(UUID tenantId, HumanCheckpoint checkpoint, UUID draftId, String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO human_checkpoint (human_checkpoint_id, tenant_id, recruitment_task_id, requirement_draft_id, "
				+ "checkpoint_type, status, input_hash, comment, version, resource_type, resource_id, resource_version, "
				+ "required_role, assignee_user_id, summary, requested_by_user_id, requested_by_display_name, requested_at, "
				+ "expires_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			checkpoint.id(), tenantId, checkpoint.taskId(), draftId, checkpoint.type(), checkpoint.status(),
			checkpoint.inputHash(), checkpoint.comment(), checkpoint.version(), checkpoint.resourceRef().type(),
			checkpoint.resourceRef().id(), checkpoint.resourceRef().version(), checkpoint.requiredRole(),
			checkpoint.assigneeUserId(), checkpoint.summary(), checkpoint.requestedBy().id(),
			checkpoint.requestedBy().displayName(), checkpoint.requestedAt(), checkpoint.expiresAt(), createdBy,
			checkpoint.requestedAt(), createdBy);
	}

	Optional<HumanCheckpoint> findCheckpoint(UUID tenantId, UUID checkpointId) {
		return jdbcTemplate.query(
			"SELECT * FROM human_checkpoint WHERE tenant_id = ? AND human_checkpoint_id = ?",
			(resultSet, rowNum) -> mapCheckpoint(resultSet),
			tenantId,
			checkpointId).stream().findFirst();
	}

	int updateCheckpoint(UUID tenantId, HumanCheckpoint previous, HumanCheckpoint updated, String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE human_checkpoint SET status = ?, decision = ?, comment = ?, decided_by_user_id = ?, "
				+ "decided_by_display_name = ?, decided_at = ?, version = ?, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND human_checkpoint_id = ? AND version = ? AND status = 'PENDING'",
			updated.status(), updated.decision(), updated.comment(),
			updated.decidedBy() == null ? null : updated.decidedBy().id(),
			updated.decidedBy() == null ? null : updated.decidedBy().displayName(),
			updated.decidedAt(), updated.version(), updated.decidedAt(), updatedBy,
			tenantId, previous.id(), previous.version());
	}

	<T> Optional<StoredCommand<T>> findCommand(
			UUID tenantId,
			String operation,
			UUID idempotencyKey,
			Class<T> responseType) {
		return jdbcTemplate.query(
			"SELECT request_hash, response_json, result_version FROM position_plan_command "
				+ "WHERE tenant_id = ? AND operation = ? AND idempotency_key = ?",
			(resultSet, rowNum) -> new StoredCommand<>(
				resultSet.getString("request_hash"),
				read(resultSet.getString("response_json"), responseType),
				resultSet.getLong("result_version")),
			tenantId,
			operation,
			idempotencyKey).stream().findFirst();
	}

	void insertCommand(
			UUID tenantId,
			UUID taskId,
			UUID resourceId,
			String operation,
			UUID idempotencyKey,
			String requestHash,
			Object response,
			long resultVersion,
			String createdBy) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcTemplate.update(
			"INSERT INTO position_plan_command (position_plan_command_id, tenant_id, recruitment_task_id, "
				+ "resource_id, operation, idempotency_key, request_hash, response_json, response_type, result_version, "
				+ "created_by, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			UUID.randomUUID(), tenantId, taskId, resourceId, operation, idempotencyKey, requestHash,
			json(response), response.getClass().getSimpleName(), resultVersion, createdBy, now, createdBy);
	}

	void appendAudit(
			UUID tenantId,
			UserRef actor,
			String resourceType,
			UUID resourceId,
			String action,
			UUID requestId,
			String traceId,
			Object payload,
			OffsetDateTime occurredAt) {
		jdbcTemplate.queryForObject(
			"SELECT tenant_id FROM tenant WHERE tenant_id = ? FOR UPDATE",
			UUID.class,
			tenantId);
		Optional<AuditHead> head = jdbcTemplate.query(
			"SELECT tenant_sequence, event_hash FROM audit_event WHERE tenant_id = ? "
				+ "ORDER BY tenant_sequence DESC LIMIT 1",
			(resultSet, rowNum) -> new AuditHead(
				resultSet.getLong("tenant_sequence"), resultSet.getString("event_hash")),
			tenantId).stream().findFirst();
		long sequence = head.map(value -> value.sequence() + 1).orElse(1L);
		String previousHash = head.map(AuditHead::eventHash).orElse(null);
		String payloadJson = json(payload);
		String eventHash = hasher.sha256Value(new AuditHashContent(
			tenantId, sequence, previousHash, actor.id(), resourceType, resourceId, action,
			traceId, requestId, payloadJson, occurredAt));
		jdbcTemplate.update(
			"INSERT INTO audit_event (audit_event_id, tenant_id, tenant_sequence, actor_type, actor_id, "
				+ "resource_type, resource_id, action, outcome, trace_id, request_id, payload, previous_hash, "
				+ "event_hash, occurred_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, 'USER', ?, ?, ?, ?, 'SUCCEEDED', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			UUID.randomUUID(), tenantId, sequence, actor.id().toString(), resourceType, resourceId.toString(),
			action, traceId, requestId, payloadJson, previousHash, eventHash, occurredAt,
			actor.id().toString(), occurredAt, actor.id().toString());
	}

	private AgentRun mapAgentRun(ResultSet resultSet) throws SQLException {
		return new AgentRun(
			resultSet.getObject("agent_run_id", UUID.class),
			resultSet.getObject("recruitment_task_id", UUID.class),
			resultSet.getString("run_type"),
			resultSet.getString("status"),
			resultSet.getString("workflow_version"),
			resultSet.getString("trace_id"),
			resultSet.getString("failure_code"),
			resultSet.getObject("created_at", OffsetDateTime.class),
			resultSet.getObject("started_at", OffsetDateTime.class),
			resultSet.getObject("finished_at", OffsetDateTime.class));
	}

	private HumanCheckpoint mapCheckpoint(ResultSet resultSet) throws SQLException {
		UUID decidedById = resultSet.getObject("decided_by_user_id", UUID.class);
		return new HumanCheckpoint(
			resultSet.getObject("human_checkpoint_id", UUID.class),
			resultSet.getObject("recruitment_task_id", UUID.class),
			resultSet.getString("checkpoint_type"),
			new ResourceRef(
				resultSet.getString("resource_type"),
				resultSet.getObject("resource_id", UUID.class),
				resultSet.getLong("resource_version")),
			resultSet.getString("status"),
			resultSet.getString("required_role"),
			resultSet.getObject("assignee_user_id", UUID.class),
			resultSet.getString("input_hash"),
			resultSet.getLong("version"),
			resultSet.getString("summary"),
			new UserRef(
				resultSet.getObject("requested_by_user_id", UUID.class),
				resultSet.getString("requested_by_display_name")),
			resultSet.getObject("requested_at", OffsetDateTime.class),
			resultSet.getObject("expires_at", OffsetDateTime.class),
			resultSet.getString("decision"),
			resultSet.getString("comment"),
			decidedById == null ? null : new UserRef(decidedById, resultSet.getString("decided_by_display_name")),
			resultSet.getObject("decided_at", OffsetDateTime.class));
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize position plan data", exception);
		}
	}

	private <T> T read(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to deserialize position plan data", exception);
		}
	}

	record StoredCommand<T>(String requestHash, T response, long resultVersion) {
	}

	private record AuditHead(long sequence, String eventHash) {
	}

	private record AuditHashContent(
			UUID tenantId,
			long sequence,
			String previousHash,
			UUID actorId,
			String resourceType,
			UUID resourceId,
			String action,
			String traceId,
			UUID requestId,
			String payload,
			OffsetDateTime occurredAt) {
	}
}

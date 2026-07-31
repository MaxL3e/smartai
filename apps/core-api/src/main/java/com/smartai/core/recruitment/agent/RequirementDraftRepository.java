package com.smartai.core.recruitment.agent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Draft;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ExternalRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Fields;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Task;

@Repository
public class RequirementDraftRepository {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final RawInputCipher cipher;

	public RequirementDraftRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, RawInputCipher cipher) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.cipher = cipher;
	}

	boolean tenantExists(UUID tenantId) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM tenant WHERE tenant_id = ? AND status = 'ACTIVE'",
			Integer.class,
			tenantId);
		return count != null && count == 1;
	}

	void insert(UUID tenantId, UUID idempotencyKey, String requestHash, Draft draft) {
		jdbcTemplate.update(
			"INSERT INTO requirement_draft (requirement_draft_id, tenant_id, status, raw_input_ciphertext, "
				+ "fields_json, source_job_ref_json, host_context_hash, created_by_user_id, created_by_display_name, "
				+ "expires_at, idempotency_key, request_hash, create_response_ciphertext, version, created_at, created_by, "
				+ "updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			draft.id(), tenantId, draft.status(), cipher.encrypt(draft.rawInput()), json(draft.fields()),
			jsonOrNull(draft.sourceJobRef()), draft.hostContextHash(), draft.createdBy().id(),
			draft.createdBy().displayName(), draft.expiresAt(), idempotencyKey, requestHash, cipher.encrypt(json(draft)),
			draft.version(), draft.createdAt(),
			draft.createdBy().id().toString(), draft.updatedAt(), draft.createdBy().id().toString());
	}

	Optional<StoredDraft> findByIdempotencyKey(UUID tenantId, UUID idempotencyKey) {
		return jdbcTemplate.query(
			"SELECT create_response_ciphertext, request_hash FROM requirement_draft "
				+ "WHERE tenant_id = ? AND idempotency_key = ?",
			(resultSet, rowNum) -> new StoredDraft(
				read(cipher.decrypt(resultSet.getString("create_response_ciphertext")), Draft.class),
				resultSet.getString("request_hash")),
			tenantId,
			idempotencyKey).stream().findFirst();
	}

	int update(UUID tenantId, Draft previous, Draft updated, String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE requirement_draft SET status = ?, raw_input_ciphertext = ?, fields_json = ?, "
				+ "converted_task_id = ?, version = ?, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND requirement_draft_id = ? AND version = ?",
			updated.status(), cipher.encrypt(updated.rawInput()), json(updated.fields()),
			updated.convertedTaskRef() == null ? null : updated.convertedTaskRef().id(),
			updated.version(), updated.updatedAt(), updatedBy,
			tenantId, previous.id(), previous.version());
	}

	int linkConvertedTask(UUID tenantId, UUID draftId, long version, UUID taskId, OffsetDateTime updatedAt, String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE requirement_draft SET converted_task_id = ?, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND requirement_draft_id = ? AND version = ? AND status = 'CONVERTED' "
				+ "AND converted_task_id IS NULL",
			taskId, updatedAt, updatedBy, tenantId, draftId, version);
	}

	<T> Optional<StoredCommand<T>> findCommand(
			UUID tenantId,
			String operation,
			UUID idempotencyKey,
			Class<T> responseType) {
		return jdbcTemplate.query(
			"SELECT request_hash, response_ciphertext, result_version FROM requirement_draft_command "
				+ "WHERE tenant_id = ? AND operation = ? AND idempotency_key = ?",
			(resultSet, rowNum) -> new StoredCommand<>(
				resultSet.getString("request_hash"),
				read(cipher.decrypt(resultSet.getString("response_ciphertext")), responseType),
				resultSet.getLong("result_version")),
			tenantId,
			operation,
			idempotencyKey).stream().findFirst();
	}

	void insertCommand(
			UUID tenantId,
			UUID draftId,
			String operation,
			UUID idempotencyKey,
			String requestHash,
			Object response,
			long resultVersion,
			String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO requirement_draft_command (requirement_draft_command_id, tenant_id, "
				+ "requirement_draft_id, operation, idempotency_key, request_hash, response_ciphertext, "
				+ "result_version, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
			UUID.randomUUID(), tenantId, draftId, operation, idempotencyKey, requestHash,
			cipher.encrypt(json(response)), resultVersion, createdBy);
	}

	void insertTask(UUID tenantId, UUID draftId, Task task, String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO recruitment_task (recruitment_task_id, tenant_id, source_draft_id, task_no, "
				+ "task_json, version, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			task.id(), tenantId, draftId, task.taskNo(), json(task), task.version(),
			task.createdAt(), createdBy, task.updatedAt(), createdBy);
	}

	void insertCreationCheckpoint(
			UUID tenantId,
			UUID draftId,
			UUID checkpointId,
			Task task,
			String inputHash,
			String comment,
			UserRef decidedBy) {
		jdbcTemplate.update(
			"INSERT INTO human_checkpoint (human_checkpoint_id, tenant_id, recruitment_task_id, "
				+ "requirement_draft_id, checkpoint_type, status, input_hash, comment, decided_by_user_id, "
				+ "decided_by_display_name, decided_at, version, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, 'CREATE_TASK', 'APPROVED', ?, ?, ?, ?, ?, 1, ?, ?, ?)",
			checkpointId, tenantId, task.id(), draftId, inputHash, comment, decidedBy.id(),
			decidedBy.displayName(), task.createdAt(), decidedBy.id().toString(), task.createdAt(),
			decidedBy.id().toString());
	}

	Optional<Task> findTask(UUID tenantId, UUID taskId) {
		return jdbcTemplate.query(
			"SELECT task_json FROM recruitment_task WHERE tenant_id = ? AND recruitment_task_id = ?",
			(resultSet, rowNum) -> read(resultSet.getString("task_json"), Task.class),
			tenantId,
			taskId).stream().findFirst();
	}

	Optional<Draft> find(UUID tenantId, UUID draftId) {
		return jdbcTemplate.query(
			"SELECT requirement_draft_id, status, raw_input_ciphertext, fields_json, source_job_ref_json, "
				+ "host_context_hash, created_by_user_id, created_by_display_name, expires_at, converted_task_id, "
				+ "version, created_at, updated_at FROM requirement_draft "
				+ "WHERE tenant_id = ? AND requirement_draft_id = ?",
			(resultSet, rowNum) -> map(resultSet),
			tenantId,
			draftId).stream().findFirst();
	}

	private Draft map(ResultSet resultSet) throws SQLException {
		UUID convertedTaskId = resultSet.getObject("converted_task_id", UUID.class);
		ResourceRef convertedTaskRef = convertedTaskId == null
			? null
			: new ResourceRef("RecruitmentTask", convertedTaskId, 1L);
		return new Draft(
			resultSet.getObject("requirement_draft_id", UUID.class),
			resultSet.getString("status"),
			resultSet.getLong("version"),
			cipher.decrypt(resultSet.getString("raw_input_ciphertext")),
			read(resultSet.getString("fields_json"), Fields.class),
			readNullable(resultSet.getString("source_job_ref_json"), ExternalRef.class),
			resultSet.getString("host_context_hash"),
			new UserRef(
				resultSet.getObject("created_by_user_id", UUID.class),
				resultSet.getString("created_by_display_name")),
			resultSet.getObject("created_at", OffsetDateTime.class),
			resultSet.getObject("updated_at", OffsetDateTime.class),
			resultSet.getObject("expires_at", OffsetDateTime.class),
			convertedTaskRef);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize requirement draft", exception);
		}
	}

	private String jsonOrNull(Object value) {
		return value == null ? null : json(value);
	}

	private <T> T read(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to deserialize requirement draft", exception);
		}
	}

	private <T> T readNullable(String value, Class<T> type) {
		return value == null ? null : read(value, type);
	}

	record StoredDraft(Draft draft, String requestHash) {
	}

	record StoredCommand<T>(String requestHash, T response, long resultVersion) {
	}
}

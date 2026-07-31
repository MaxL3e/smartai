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
import com.smartai.core.recruitment.agent.MatchingModels.CandidateFacts;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateInputReceipt;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateSummary;
import com.smartai.core.recruitment.agent.MatchingModels.MatchResult;
import com.smartai.core.recruitment.agent.MatchingModels.MatchRun;
import com.smartai.core.recruitment.agent.MatchingModels.NormalizedCandidate;
import com.smartai.core.recruitment.agent.MatchingModels.ResumeSection;
import com.smartai.core.recruitment.agent.MatchingModels.TaskCandidate;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ExternalRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;

@Repository
public class MatchingRepository {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final RawInputCipher cipher;

	public MatchingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, RawInputCipher cipher) {
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

	Optional<CandidateRow> findCandidateByExternal(UUID tenantId, String sourceSystem, String externalCandidateId) {
		return jdbcTemplate.query(
			"SELECT * FROM candidate WHERE tenant_id = ? AND source_system = ? AND external_candidate_id = ?",
			(resultSet, rowNum) -> mapCandidateRow(resultSet),
			tenantId,
			sourceSystem,
			externalCandidateId).stream().findFirst();
	}

	void insertCandidate(UUID tenantId, CandidateRow candidate, String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO candidate (candidate_id, tenant_id, candidate_no, display_name_ciphertext, consent_status, "
				+ "source_type, source_system, external_candidate_id, external_version, connector_id, "
				+ "source_application_ref_json, version, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			candidate.id(), tenantId, candidate.candidateNo(), cipher.encrypt(candidate.displayName()),
			candidate.consentStatus(), candidate.sourceType(), candidate.sourceSystem(), candidate.externalCandidateId(),
			candidate.externalVersion(), candidate.connectorId(), jsonOrNull(candidate.sourceApplicationRef()),
			candidate.version(), candidate.createdAt(), createdBy, candidate.updatedAt(), createdBy);
	}

	int updateCandidate(UUID tenantId, CandidateRow previous, CandidateRow updated, String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE candidate SET display_name_ciphertext = ?, consent_status = ?, source_type = ?, external_version = ?, "
				+ "connector_id = ?, source_application_ref_json = ?, version = ?, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND candidate_id = ? AND version = ?",
			cipher.encrypt(updated.displayName()), updated.consentStatus(), updated.sourceType(), updated.externalVersion(),
			updated.connectorId(), jsonOrNull(updated.sourceApplicationRef()), updated.version(), updated.updatedAt(),
			updatedBy, tenantId, previous.id(), previous.version());
	}

	int nextResumeVersionNo(UUID tenantId, UUID candidateId) {
		Integer current = jdbcTemplate.queryForObject(
			"SELECT COALESCE(MAX(version_no), 0) FROM resume_version WHERE tenant_id = ? AND candidate_id = ?",
			Integer.class,
			tenantId,
			candidateId);
		return current == null ? 1 : current + 1;
	}

	Optional<NormalizedCandidate> findResumeBySourceVersion(
			UUID tenantId,
			UUID candidateId,
			String sourceVersion) {
		return jdbcTemplate.query(
			candidateResumeSelect() + " WHERE c.tenant_id = ? AND c.candidate_id = ? AND r.source_version = ?",
			(resultSet, rowNum) -> mapNormalizedCandidate(resultSet),
			tenantId,
			candidateId,
			sourceVersion).stream().findFirst();
	}

	void insertResume(
			UUID tenantId,
			UUID resumeId,
			UUID candidateId,
			int versionNo,
			String sourceVersion,
			List<ResumeSection> sections,
			CandidateFacts facts,
			String contentHash,
			OffsetDateTime sourceUpdatedAt,
			OffsetDateTime now,
			String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO resume_version (resume_version_id, tenant_id, candidate_id, version_no, source_version, "
				+ "normalized_input_ciphertext, content_hash, normalizer_kind, source_updated_at, version, created_at, "
				+ "created_by, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, 'DETERMINISTIC_NORMALIZER', ?, 1, ?, ?, ?, ?)",
			resumeId, tenantId, candidateId, versionNo, sourceVersion,
			cipher.encrypt(json(new NormalizedPayload(sections, facts))), contentHash, sourceUpdatedAt,
			now, createdBy, now, createdBy);
	}

	Optional<StoredCandidateCommand> findCandidateCommand(UUID tenantId, UUID idempotencyKey) {
		return jdbcTemplate.query(
			"SELECT request_hash, response_ciphertext, result_version FROM candidate_input_command "
				+ "WHERE tenant_id = ? AND idempotency_key = ?",
			(resultSet, rowNum) -> new StoredCandidateCommand(
				resultSet.getString("request_hash"),
				read(cipher.decrypt(resultSet.getString("response_ciphertext")), CandidateInputReceipt.class),
				resultSet.getLong("result_version")),
			tenantId,
			idempotencyKey).stream().findFirst();
	}

	void insertCandidateCommand(
			UUID tenantId,
			UUID idempotencyKey,
			String requestHash,
			CandidateInputReceipt response,
			long resultVersion,
			OffsetDateTime now,
			String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO candidate_input_command (candidate_input_command_id, tenant_id, idempotency_key, request_hash, "
				+ "response_ciphertext, result_version, version, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)",
			UUID.randomUUID(), tenantId, idempotencyKey, requestHash, cipher.encrypt(json(response)), resultVersion,
			now, createdBy, now, createdBy);
	}

	List<NormalizedCandidate> listLatestCandidates(UUID tenantId) {
		return jdbcTemplate.query(
			candidateResumeSelect()
				+ " WHERE c.tenant_id = ? AND r.version_no = (SELECT MAX(r2.version_no) FROM resume_version r2 "
				+ "WHERE r2.tenant_id = c.tenant_id AND r2.candidate_id = c.candidate_id) "
				+ "ORDER BY c.candidate_no",
			(resultSet, rowNum) -> mapNormalizedCandidate(resultSet),
			tenantId);
	}

	Optional<StoredMatchRun> findMatchRunByIdempotency(UUID tenantId, UUID idempotencyKey) {
		return jdbcTemplate.query(
			"SELECT request_hash, match_run_json FROM match_run WHERE tenant_id = ? AND idempotency_key = ?",
			(resultSet, rowNum) -> new StoredMatchRun(
				resultSet.getString("request_hash"), read(resultSet.getString("match_run_json"), MatchRun.class)),
			tenantId,
			idempotencyKey).stream().findFirst();
	}

	void insertMatchRun(
			UUID tenantId,
			MatchRun run,
			UUID planId,
			UUID scorecardId,
			UUID idempotencyKey,
			String requestHash,
			String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO match_run (match_run_id, tenant_id, recruitment_task_id, position_plan_version_id, "
				+ "scorecard_version_id, match_run_json, status, generator_kind, idempotency_key, request_hash, version, "
				+ "created_at, created_by, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, 'DETERMINISTIC_RULES', ?, ?, ?, ?, ?, ?, ?)",
			run.id(), tenantId, run.taskId(), planId, scorecardId, json(run), run.status(), idempotencyKey,
			requestHash, run.version(), run.createdAt(), createdBy, run.createdAt(), createdBy);
	}

	int updateMatchRun(UUID tenantId, MatchRun previous, MatchRun updated, String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE match_run SET match_run_json = ?, status = ?, version = ?, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND match_run_id = ? AND version = ?",
			json(updated), updated.status(), updated.version(), updated.finishedAt(), updatedBy,
			tenantId, previous.id(), previous.version());
	}

	Optional<MatchRun> findMatchRun(UUID tenantId, UUID runId) {
		return jdbcTemplate.query(
			"SELECT match_run_json FROM match_run WHERE tenant_id = ? AND match_run_id = ?",
			(resultSet, rowNum) -> read(resultSet.getString("match_run_json"), MatchRun.class),
			tenantId,
			runId).stream().findFirst();
	}

	TaskCandidate findOrInsertTaskCandidate(
			UUID tenantId,
			UUID taskId,
			NormalizedCandidate candidate,
			OffsetDateTime now,
			String createdBy) {
		Optional<TaskCandidate> existing = findTaskCandidate(tenantId, taskId, candidate.candidate().id());
		if (existing.isPresent()) return existing.get();
		UUID id = UUID.randomUUID();
		TaskCandidate taskCandidate = new TaskCandidate(
			id, taskId, candidate.candidate(), "DISCOVERED", "UNREVIEWED", 1L, null, null,
			candidate.sourceType(), candidate.sourceApplicationRef(), now, now);
		jdbcTemplate.update(
			"INSERT INTO task_candidate (task_candidate_id, tenant_id, recruitment_task_id, candidate_id, status, "
				+ "selection_status, source_type, source_application_ref_json, version, created_at, created_by, updated_at, "
				+ "updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			id, tenantId, taskId, candidate.candidate().id(), taskCandidate.status(), taskCandidate.selectionStatus(),
			taskCandidate.sourceType(), jsonOrNull(taskCandidate.sourceApplicationRef()), taskCandidate.version(),
			now, createdBy, now, createdBy);
		return taskCandidate;
	}

	void insertMatchResult(
			UUID tenantId,
			UUID candidateId,
			UUID resumeId,
			MatchResult result,
			OffsetDateTime now,
			String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO match_result (match_result_id, tenant_id, match_run_id, task_candidate_id, candidate_id, "
				+ "resume_version_id, result_ciphertext, total_score, result_rank, generator_kind, version, created_at, "
				+ "created_by, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DETERMINISTIC_RULES', ?, ?, ?, ?, ?)",
			result.id(), tenantId, result.matchRunId(), result.taskCandidateRef().id(), candidateId, resumeId,
			cipher.encrypt(json(result)), result.totalScore(), result.rank(), result.version(), now, createdBy, now, createdBy);
	}

	int updateTaskCandidateCurrent(
			UUID tenantId,
			TaskCandidate previous,
			UUID matchResultId,
			OffsetDateTime now,
			String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE task_candidate SET current_match_result_id = ?, version = version + 1, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND task_candidate_id = ? AND version = ?",
			matchResultId, now, updatedBy, tenantId, previous.id(), previous.version());
	}

	List<MatchResult> listMatchResults(UUID tenantId, UUID runId) {
		return jdbcTemplate.query(
			"SELECT result_ciphertext FROM match_result WHERE tenant_id = ? AND match_run_id = ? ORDER BY result_rank",
			(resultSet, rowNum) -> read(cipher.decrypt(resultSet.getString("result_ciphertext")), MatchResult.class),
			tenantId,
			runId);
	}

	Optional<MatchResult> findMatchResult(UUID tenantId, UUID resultId) {
		return jdbcTemplate.query(
			"SELECT result_ciphertext FROM match_result WHERE tenant_id = ? AND match_result_id = ?",
			(resultSet, rowNum) -> read(cipher.decrypt(resultSet.getString("result_ciphertext")), MatchResult.class),
			tenantId,
			resultId).stream().findFirst();
	}

	List<TaskCandidate> listTaskCandidates(UUID tenantId, UUID taskId) {
		return jdbcTemplate.query(
			"SELECT tc.*, c.candidate_no, c.display_name_ciphertext, c.consent_status, c.source_type AS candidate_source_type, "
				+ "c.source_system, c.external_candidate_id, c.external_version, c.connector_id, "
				+ "c.source_application_ref_json AS candidate_source_application_ref_json FROM task_candidate tc JOIN candidate c "
				+ "ON c.tenant_id = tc.tenant_id AND c.candidate_id = tc.candidate_id "
				+ "WHERE tc.tenant_id = ? AND tc.recruitment_task_id = ? ORDER BY c.candidate_no",
			(resultSet, rowNum) -> mapTaskCandidate(resultSet),
			tenantId,
			taskId);
	}

	private Optional<TaskCandidate> findTaskCandidate(UUID tenantId, UUID taskId, UUID candidateId) {
		return jdbcTemplate.query(
			"SELECT tc.*, c.candidate_no, c.display_name_ciphertext, c.consent_status, c.source_type AS candidate_source_type, "
				+ "c.source_system, c.external_candidate_id, c.external_version, c.connector_id, "
				+ "c.source_application_ref_json AS candidate_source_application_ref_json FROM task_candidate tc JOIN candidate c "
				+ "ON c.tenant_id = tc.tenant_id AND c.candidate_id = tc.candidate_id "
				+ "WHERE tc.tenant_id = ? AND tc.recruitment_task_id = ? AND tc.candidate_id = ?",
			(resultSet, rowNum) -> mapTaskCandidate(resultSet),
			tenantId,
			taskId,
			candidateId).stream().findFirst();
	}

	private CandidateRow mapCandidateRow(ResultSet resultSet) throws SQLException {
		return new CandidateRow(
			resultSet.getObject("candidate_id", UUID.class), resultSet.getString("candidate_no"),
			cipher.decrypt(resultSet.getString("display_name_ciphertext")), resultSet.getString("consent_status"),
			column(resultSet, "candidate_source_type", "source_type"), resultSet.getString("source_system"),
			resultSet.getString("external_candidate_id"), resultSet.getString("external_version"),
			resultSet.getObject("connector_id", UUID.class),
			readNullable(column(resultSet, "candidate_source_application_ref_json", "source_application_ref_json"), ExternalRef.class),
			resultSet.getLong("version"), resultSet.getObject("created_at", OffsetDateTime.class),
			resultSet.getObject("updated_at", OffsetDateTime.class));
	}

	private NormalizedCandidate mapNormalizedCandidate(ResultSet resultSet) throws SQLException {
		CandidateRow candidate = mapCandidateRow(resultSet);
		NormalizedPayload payload = read(
			cipher.decrypt(resultSet.getString("normalized_input_ciphertext")), NormalizedPayload.class);
		int versionNo = resultSet.getInt("resume_version_no");
		CandidateSummary summary = summary(candidate);
		return new NormalizedCandidate(
			summary, candidate.connectorId(), candidate.sourceType(), candidate.sourceApplicationRef(),
			new ResourceRef("ResumeVersion", resultSet.getObject("resume_version_id", UUID.class), versionNo),
			payload.sections(), payload.facts(), resultSet.getString("content_hash"),
			resultSet.getObject("source_updated_at", OffsetDateTime.class));
	}

	private TaskCandidate mapTaskCandidate(ResultSet resultSet) throws SQLException {
		CandidateRow candidate = mapCandidateRow(resultSet);
		UUID resultId = resultSet.getObject("current_match_result_id", UUID.class);
		UUID listId = resultSet.getObject("candidate_list_version_id", UUID.class);
		return new TaskCandidate(
			resultSet.getObject("task_candidate_id", UUID.class),
			resultSet.getObject("recruitment_task_id", UUID.class), summary(candidate),
			resultSet.getString("status"), resultSet.getString("selection_status"), resultSet.getLong("version"),
			resultId == null ? null : new ResourceRef("MatchResult", resultId, 1L),
			listId == null ? null : new ResourceRef("CandidateListVersion", listId, 1L),
			resultSet.getString("source_type"),
			readNullable(resultSet.getString("source_application_ref_json"), ExternalRef.class),
			resultSet.getObject("created_at", OffsetDateTime.class),
			resultSet.getObject("updated_at", OffsetDateTime.class));
	}

	private CandidateSummary summary(CandidateRow candidate) {
		return new CandidateSummary(
			candidate.id(), candidate.candidateNo(), candidate.displayName(), candidate.consentStatus(),
			new ExternalRef(
				candidate.sourceSystem(), candidate.externalCandidateId(), "Candidate", candidate.externalVersion()));
	}

	private static String candidateResumeSelect() {
		return "SELECT c.*, r.resume_version_id, r.version_no AS resume_version_no, r.normalized_input_ciphertext, "
			+ "r.content_hash, r.source_updated_at "
			+ "FROM candidate c JOIN resume_version r ON r.tenant_id = c.tenant_id AND r.candidate_id = c.candidate_id";
	}

	private static String column(ResultSet resultSet, String preferred, String fallback) throws SQLException {
		try {
			return resultSet.getString(preferred);
		}
		catch (SQLException exception) {
			return resultSet.getString(fallback);
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize matching data", exception);
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
			throw new IllegalStateException("Unable to deserialize matching data", exception);
		}
	}

	private <T> T readNullable(String value, Class<T> type) {
		return value == null ? null : read(value, type);
	}

	record CandidateRow(
			UUID id,
			String candidateNo,
			String displayName,
			String consentStatus,
			String sourceType,
			String sourceSystem,
			String externalCandidateId,
			String externalVersion,
			UUID connectorId,
			ExternalRef sourceApplicationRef,
			long version,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	record StoredCandidateCommand(String requestHash, CandidateInputReceipt response, long resultVersion) {
	}

	record StoredMatchRun(String requestHash, MatchRun response) {
	}

	private record NormalizedPayload(List<ResumeSection> sections, CandidateFacts facts) {
	}
}

package com.smartai.core.recruitment.agent;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListPreview;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListVersion;
import com.smartai.core.recruitment.agent.CandidateListModels.RecommendationReport;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;

@Repository
public class CandidateListRepository {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final RawInputCipher cipher;

	public CandidateListRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, RawInputCipher cipher) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.cipher = cipher;
	}

	void insertPreview(
			UUID tenantId,
			CandidateListPreview preview,
			long taskVersion,
			String createdBy,
			OffsetDateTime now) {
		jdbcTemplate.update(
			"INSERT INTO candidate_list_preview (candidate_list_preview_id, tenant_id, recruitment_task_id, "
				+ "match_run_id, preview_ciphertext, input_hash, task_version, expires_at, version, created_at, "
				+ "created_by, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)",
			preview.id(), tenantId, preview.taskId(), preview.matchRunRef().id(), cipher.encrypt(json(preview)),
			preview.inputHash(), taskVersion, preview.expiresAt(), now, createdBy, now, createdBy);
	}

	Optional<StoredPreview> findPreview(UUID tenantId, UUID previewId) {
		return jdbcTemplate.query(
			"SELECT preview_ciphertext, task_version FROM candidate_list_preview "
				+ "WHERE tenant_id = ? AND candidate_list_preview_id = ?",
			(resultSet, rowNum) -> new StoredPreview(
				read(cipher.decrypt(resultSet.getString("preview_ciphertext")), CandidateListPreview.class),
				resultSet.getLong("task_version")),
			tenantId,
			previewId).stream().findFirst();
	}

	int nextCandidateListVersionNo(UUID tenantId, UUID taskId) {
		Integer current = jdbcTemplate.queryForObject(
			"SELECT COALESCE(MAX(version_no), 0) FROM candidate_list_version "
				+ "WHERE tenant_id = ? AND recruitment_task_id = ?",
			Integer.class,
			tenantId,
			taskId);
		return current == null ? 1 : current + 1;
	}

	void insertCandidateList(UUID tenantId, CandidateListVersion list, String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO candidate_list_version (candidate_list_version_id, tenant_id, recruitment_task_id, "
				+ "version_no, candidate_list_preview_id, match_run_id, checkpoint_id, list_ciphertext, "
				+ "content_hash, version, confirmed_at, confirmed_by_user_id, confirmed_by_display_name, "
				+ "created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)",
			list.id(), tenantId, list.taskId(), list.versionNo(), list.previewRef().id(), list.matchRunRef().id(),
			list.checkpointRef().id(), cipher.encrypt(json(list)), list.contentHash(), list.confirmedAt(),
			list.confirmedBy().id(), list.confirmedBy().displayName(), list.confirmedAt(), createdBy,
			list.confirmedAt(), createdBy);
	}

	Optional<CandidateListVersion> findCandidateListByCheckpoint(UUID tenantId, UUID checkpointId) {
		return queryCandidateList(
			"SELECT list_ciphertext FROM candidate_list_version WHERE tenant_id = ? AND checkpoint_id = ?",
			tenantId,
			checkpointId);
	}

	Optional<CandidateListVersion> findCurrentCandidateList(UUID tenantId, UUID taskId) {
		return queryCandidateList(
			"SELECT list_ciphertext FROM candidate_list_version WHERE tenant_id = ? AND recruitment_task_id = ? "
				+ "ORDER BY version_no DESC LIMIT 1",
			tenantId,
			taskId);
	}

	Optional<CandidateListVersion> findCandidateList(UUID tenantId, UUID listId) {
		return queryCandidateList(
			"SELECT list_ciphertext FROM candidate_list_version WHERE tenant_id = ? AND candidate_list_version_id = ?",
			tenantId,
			listId);
	}

	int confirmTaskCandidate(
			UUID tenantId,
			ResourceRef taskCandidateRef,
			ResourceRef matchResultRef,
			UUID candidateListVersionId,
			OffsetDateTime now,
			String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE task_candidate SET status = 'SELECTED', selection_status = 'CONFIRMED', "
				+ "candidate_list_version_id = ?, version = version + 1, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND task_candidate_id = ? AND version = ? AND current_match_result_id = ?",
			candidateListVersionId, now, updatedBy, tenantId, taskCandidateRef.id(), taskCandidateRef.version(),
			matchResultRef.id());
	}

	int nextReportVersionNo(UUID tenantId, UUID taskId) {
		Integer current = jdbcTemplate.queryForObject(
			"SELECT COALESCE(MAX(version_no), 0) FROM recommendation_report_version "
				+ "WHERE tenant_id = ? AND recruitment_task_id = ?",
			Integer.class,
			tenantId,
			taskId);
		return current == null ? 1 : current + 1;
	}

	void insertReport(UUID tenantId, RecommendationReport report, String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO recommendation_report_version (recommendation_report_version_id, tenant_id, "
				+ "recruitment_task_id, version_no, candidate_list_version_id, position_plan_version_id, "
				+ "scorecard_version_id, match_run_id, report_ciphertext, content_hash, version, generated_at, "
				+ "generated_by_user_id, generated_by_display_name, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)",
			report.id(), tenantId, report.taskId(), report.versionNo(), report.candidateListVersionRef().id(),
			report.positionPlanVersionRef().id(), report.scorecardVersionRef().id(), report.matchRunRef().id(),
			cipher.encrypt(json(report)), report.contentHash(), report.generatedAt(), report.generatedBy().id(),
			report.generatedBy().displayName(), report.generatedAt(), createdBy, report.generatedAt(), createdBy);
	}

	Optional<RecommendationReport> findCurrentReport(UUID tenantId, UUID taskId) {
		return queryReport(
			"SELECT report_ciphertext FROM recommendation_report_version WHERE tenant_id = ? "
				+ "AND recruitment_task_id = ? ORDER BY version_no DESC LIMIT 1",
			tenantId,
			taskId);
	}

	Optional<RecommendationReport> findReport(UUID tenantId, UUID reportId) {
		return queryReport(
			"SELECT report_ciphertext FROM recommendation_report_version WHERE tenant_id = ? "
				+ "AND recommendation_report_version_id = ?",
			tenantId,
			reportId);
	}

	private Optional<CandidateListVersion> queryCandidateList(String sql, Object... arguments) {
		return jdbcTemplate.query(
			sql,
			(resultSet, rowNum) -> read(
				cipher.decrypt(resultSet.getString("list_ciphertext")), CandidateListVersion.class),
			arguments).stream().findFirst();
	}

	private Optional<RecommendationReport> queryReport(String sql, Object... arguments) {
		return jdbcTemplate.query(
			sql,
			(resultSet, rowNum) -> read(
				cipher.decrypt(resultSet.getString("report_ciphertext")), RecommendationReport.class),
			arguments).stream().findFirst();
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize candidate list data", exception);
		}
	}

	private <T> T read(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to deserialize candidate list data", exception);
		}
	}

	record StoredPreview(CandidateListPreview preview, long taskVersion) {
	}
}

package com.smartai.core.recruitment.agent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateInputReceipt;
import com.smartai.core.recruitment.agent.ResumeFileModels.ParsedResumeProfile;
import com.smartai.core.recruitment.agent.ResumeFileModels.ResumeFile;

@Repository
public class ResumeFileRepository {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	ResumeFileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	boolean tenantExists(UUID tenantId) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM tenant WHERE tenant_id = ? AND status = 'ACTIVE'",
			Integer.class,
			tenantId);
		return count != null && count == 1;
	}

	Optional<DocumentRow> lockDocumentByExternal(UUID tenantId, String sourceSystem, String externalCandidateId) {
		return jdbcTemplate.query(
			"SELECT * FROM resume_document WHERE tenant_id = ? AND source_system = ? "
				+ "AND external_candidate_id = ? FOR UPDATE",
			(resultSet, rowNum) -> mapDocument(resultSet),
			tenantId,
			sourceSystem,
			externalCandidateId).stream().findFirst();
	}

	void insertDocument(UUID tenantId, DocumentRow document, String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO resume_document (resume_document_id, tenant_id, source_system, external_candidate_id, "
				+ "status, current_file_version_id, candidate_id, version, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?)",
			document.id(), tenantId, document.sourceSystem(), document.externalCandidateId(), document.status(),
			document.version(), document.createdAt(), createdBy, document.updatedAt(), createdBy);
	}

	int nextVersionNo(UUID tenantId, UUID documentId) {
		Integer current = jdbcTemplate.queryForObject(
			"SELECT COALESCE(MAX(version_no), 0) FROM resume_file_version "
				+ "WHERE tenant_id = ? AND resume_document_id = ?",
			Integer.class,
			tenantId,
			documentId);
		return current == null ? 1 : current + 1;
	}

	void insertVersion(
			UUID tenantId,
			UUID versionId,
			UUID documentId,
			int versionNo,
			String fileName,
			String declaredMimeType,
			String detectedMimeType,
			byte[] bytes,
			String sha256,
			ResumeFileModels.ParserOutcome outcome,
			CandidateInputReceipt candidateReceipt,
			OffsetDateTime now,
			String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO resume_file_version (resume_file_version_id, tenant_id, resume_document_id, version_no, "
				+ "file_name, declared_mime_type, detected_mime_type, size_bytes, sha256, raw_base64, extracted_text, "
				+ "parse_status, parser_version, failure_code, retryable, parsed_json, candidate_receipt_json, "
				+ "normalized_resume_version_id, version, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)",
			versionId, tenantId, documentId, versionNo, fileName, declaredMimeType, detectedMimeType, bytes.length,
			sha256, java.util.Base64.getEncoder().encodeToString(bytes), outcome.extractedText(), outcome.parseStatus(),
			outcome.parserVersion(), outcome.failureCode(), outcome.retryable(), json(outcome.parsedProfile()),
			jsonOrNull(candidateReceipt),
			candidateReceipt == null ? null : candidateReceipt.resumeVersionRef().id(),
			now, createdBy, now, createdBy);
	}

	int updateDocumentCurrent(
			UUID tenantId,
			DocumentRow previous,
			UUID fileVersionId,
			UUID candidateId,
			String status,
			OffsetDateTime now,
			String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE resume_document SET current_file_version_id = ?, candidate_id = ?, status = ?, "
				+ "version = version + 1, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND resume_document_id = ? AND version = ?",
			fileVersionId, candidateId, status, now, updatedBy, tenantId, previous.id(), previous.version());
	}

	Optional<ResumeFile> findByHash(UUID tenantId, UUID documentId, String sha256) {
		return queryFiles(
			resumeFileSelect() + " WHERE d.tenant_id = ? AND d.resume_document_id = ? AND v.sha256 = ?",
			tenantId,
			documentId,
			sha256).stream().findFirst();
	}

	Optional<ResumeFile> find(UUID tenantId, UUID documentId) {
		return queryFiles(
			resumeFileSelect() + " WHERE d.tenant_id = ? AND d.resume_document_id = ? "
				+ "AND v.resume_file_version_id = d.current_file_version_id",
			tenantId,
			documentId).stream().findFirst();
	}

	List<ResumeFile> list(UUID tenantId, String parseStatus, int limit, int offset) {
		StringBuilder sql = new StringBuilder(resumeFileSelect())
			.append(" WHERE d.tenant_id = ? AND v.resume_file_version_id = d.current_file_version_id");
		List<Object> arguments = new ArrayList<>();
		arguments.add(tenantId);
		if (parseStatus != null) {
			sql.append(" AND v.parse_status = ?");
			arguments.add(parseStatus);
		}
		sql.append(" ORDER BY d.updated_at DESC, d.resume_document_id DESC LIMIT ? OFFSET ?");
		arguments.add(limit);
		arguments.add(offset);
		return queryFiles(sql.toString(), arguments.toArray());
	}

	Optional<StoredCommand> findCommand(UUID tenantId, UUID idempotencyKey) {
		return jdbcTemplate.query(
			"SELECT request_hash, response_json FROM resume_file_command "
				+ "WHERE tenant_id = ? AND operation = 'CREATE' AND idempotency_key = ?",
			(resultSet, rowNum) -> new StoredCommand(
				resultSet.getString("request_hash"), read(resultSet.getString("response_json"), ResumeFile.class)),
			tenantId,
			idempotencyKey).stream().findFirst();
	}

	void insertCommand(
			UUID tenantId,
			UUID documentId,
			UUID idempotencyKey,
			String requestHash,
			ResumeFile response,
			String createdBy,
			OffsetDateTime now) {
		jdbcTemplate.update(
			"INSERT INTO resume_file_command (resume_file_command_id, tenant_id, resume_document_id, operation, "
				+ "idempotency_key, request_hash, response_json, result_version, version, created_at, created_by, "
				+ "updated_at, updated_by) VALUES (?, ?, ?, 'CREATE', ?, ?, ?, ?, 1, ?, ?, ?, ?)",
			UUID.randomUUID(), tenantId, documentId, idempotencyKey, requestHash, json(response), response.fileVersion(),
			now, createdBy, now, createdBy);
	}

	private List<ResumeFile> queryFiles(String sql, Object... arguments) {
		return jdbcTemplate.query(sql, (resultSet, rowNum) -> mapResumeFile(resultSet), arguments);
	}

	private ResumeFile mapResumeFile(ResultSet resultSet) throws SQLException {
		CandidateInputReceipt receipt = readNullable(
			resultSet.getString("candidate_receipt_json"), CandidateInputReceipt.class);
		ParsedResumeProfile profile = read(resultSet.getString("parsed_json"), ParsedResumeProfile.class);
		return new ResumeFile(
			resultSet.getObject("resume_document_id", UUID.class),
			resultSet.getInt("version_no"),
			resultSet.getString("file_name"),
			resultSet.getString("detected_mime_type"),
			resultSet.getLong("size_bytes"),
			resultSet.getString("sha256"),
			resultSet.getString("parse_status"),
			resultSet.getString("failure_code"),
			resultSet.getString("parser_version"),
			resultSet.getBoolean("retryable"),
			resultSet.getString("extracted_text"),
			profile.evidence(),
			profile,
			receipt == null ? null : receipt.candidate(),
			receipt == null ? null : receipt.resumeVersionRef(),
			receipt,
			resultSet.getString("source_system"),
			resultSet.getString("external_candidate_id"),
			resultSet.getObject("file_created_at", OffsetDateTime.class),
			resultSet.getObject("document_updated_at", OffsetDateTime.class));
	}

	private static String resumeFileSelect() {
		return "SELECT d.resume_document_id, d.source_system, d.external_candidate_id, "
			+ "d.updated_at AS document_updated_at, v.*, v.created_at AS file_created_at "
			+ "FROM resume_document d JOIN resume_file_version v ON v.tenant_id = d.tenant_id "
			+ "AND v.resume_document_id = d.resume_document_id";
	}

	private static DocumentRow mapDocument(ResultSet resultSet) throws SQLException {
		return new DocumentRow(
			resultSet.getObject("resume_document_id", UUID.class),
			resultSet.getString("source_system"),
			resultSet.getString("external_candidate_id"),
			resultSet.getString("status"),
			resultSet.getObject("candidate_id", UUID.class),
			resultSet.getLong("version"),
			resultSet.getObject("created_at", OffsetDateTime.class),
			resultSet.getObject("updated_at", OffsetDateTime.class));
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize resume file data", exception);
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
			throw new IllegalStateException("Unable to deserialize resume file data", exception);
		}
	}

	private <T> T readNullable(String value, Class<T> type) {
		return value == null ? null : read(value, type);
	}

	record DocumentRow(
			UUID id,
			String sourceSystem,
			String externalCandidateId,
			String status,
			UUID candidateId,
			long version,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	record StoredCommand(String requestHash, ResumeFile response) {
	}
}

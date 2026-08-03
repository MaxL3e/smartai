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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartai.core.recruitment.agent.KnowledgeModels.Chunk;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeDocument;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeVersion;
import com.smartai.core.recruitment.agent.KnowledgeModels.StoredUpload;
import com.smartai.core.recruitment.agent.KnowledgeModels.VersionContent;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

@Repository
public class KnowledgeRepository {

	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	KnowledgeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	void insertDocument(UUID tenantId, KnowledgeDocument document, String createdBy) {
		jdbcTemplate.update(
			"INSERT INTO knowledge_document (knowledge_document_id, tenant_id, title, knowledge_type, "
				+ "owner_resource_type, owner_resource_id, owner_resource_version, classification, status, tags_json, "
				+ "access_policy_id, retention_until, current_version_id, version, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			document.id(), tenantId, document.title(), document.type(), document.ownerOrganizationRef().type(),
			document.ownerOrganizationRef().id(), document.ownerOrganizationRef().version(), document.classification(),
			document.status(), json(document.tags()), document.accessPolicyId(), document.retentionUntil(), null,
			document.version(), document.createdAt(), createdBy, document.updatedAt(), createdBy);
	}

	Optional<KnowledgeDocument> findDocument(UUID tenantId, UUID documentId) {
		return jdbcTemplate.query(
			"SELECT * FROM knowledge_document WHERE tenant_id = ? AND knowledge_document_id = ?",
			(resultSet, rowNum) -> mapDocument(tenantId, resultSet),
			tenantId,
			documentId).stream().findFirst();
	}

	Optional<KnowledgeDocument> lockDocument(UUID tenantId, UUID documentId) {
		return jdbcTemplate.query(
			"SELECT * FROM knowledge_document WHERE tenant_id = ? AND knowledge_document_id = ? FOR UPDATE",
			(resultSet, rowNum) -> mapDocument(tenantId, resultSet),
			tenantId,
			documentId).stream().findFirst();
	}

	List<KnowledgeDocument> listDocuments(
			UUID tenantId,
			String type,
			String status,
			int limit,
			int offset) {
		StringBuilder sql = new StringBuilder(
			"SELECT * FROM knowledge_document WHERE tenant_id = ?");
		List<Object> arguments = new ArrayList<>();
		arguments.add(tenantId);
		if (type != null) {
			sql.append(" AND knowledge_type = ?");
			arguments.add(type);
		}
		if (status != null) {
			sql.append(" AND status = ?");
			arguments.add(status);
		}
		sql.append(" ORDER BY updated_at DESC, knowledge_document_id DESC LIMIT ? OFFSET ?");
		arguments.add(limit);
		arguments.add(offset);
		return jdbcTemplate.query(sql.toString(), (resultSet, rowNum) -> mapDocument(tenantId, resultSet), arguments.toArray());
	}

	int updateDocument(UUID tenantId, KnowledgeDocument previous, KnowledgeDocument updated, String updatedBy) {
		return jdbcTemplate.update(
			"UPDATE knowledge_document SET title = ?, classification = ?, status = ?, tags_json = ?, "
				+ "access_policy_id = ?, retention_until = ?, current_version_id = ?, version = ?, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND knowledge_document_id = ? AND version = ?",
			updated.title(), updated.classification(), updated.status(), json(updated.tags()), updated.accessPolicyId(),
			updated.retentionUntil(), updated.currentVersion() == null ? null : updated.currentVersion().id(),
			updated.version(), updated.updatedAt(), updatedBy, tenantId, previous.id(), previous.version());
	}

	int nextVersionNo(UUID tenantId, UUID documentId) {
		Integer current = jdbcTemplate.queryForObject(
			"SELECT COALESCE(MAX(version_no), 0) FROM knowledge_version "
				+ "WHERE tenant_id = ? AND knowledge_document_id = ?",
			Integer.class,
			tenantId,
			documentId);
		return current == null ? 1 : current + 1;
	}

	void insertVersion(
			UUID tenantId,
			VersionContent version,
			List<Chunk> chunks,
			String createdBy,
			OffsetDateTime now) {
		KnowledgeVersion value = version.version();
		jdbcTemplate.update(
			"INSERT INTO knowledge_version (knowledge_version_id, tenant_id, knowledge_document_id, version_no, "
				+ "file_name, mime_type, size_bytes, sha256, content_hash, content_text, publication_status, "
				+ "parse_status, index_status, parser_version, effective_from, effective_to, change_summary, "
				+ "approval_checkpoint_id, approval_checkpoint_version, approved_by_user_id, approved_by_display_name, "
				+ "approved_at, failure_code, version, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			value.id(), tenantId, value.documentId(), value.versionNo(), value.fileName(), value.mimeType(),
			version.sizeBytes(), value.sha256(), value.contentHash(), version.contentText(), value.publicationStatus(),
			value.parseStatus(), value.indexStatus(), value.parserVersion(), value.effectiveFrom(), value.effectiveTo(),
			version.changeSummary(), null, null, null, null, null, value.failureCode(), value.version(), value.createdAt(),
			createdBy, now, createdBy);
		for (Chunk chunk : chunks) {
			jdbcTemplate.update(
				"INSERT INTO knowledge_chunk (knowledge_chunk_id, tenant_id, knowledge_version_id, chunk_no, "
					+ "chunk_text, quote_hash, start_offset, end_offset, created_by, updated_by) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				chunk.id(), tenantId, chunk.versionId(), chunk.chunkNo(), chunk.text(), chunk.quoteHash(),
				chunk.startOffset(), chunk.endOffset(), createdBy, createdBy);
		}
	}

	Optional<VersionContent> findVersion(UUID tenantId, UUID versionId) {
		return jdbcTemplate.query(
			"SELECT * FROM knowledge_version WHERE tenant_id = ? AND knowledge_version_id = ?",
			(resultSet, rowNum) -> mapVersionContent(resultSet),
			tenantId,
			versionId).stream().findFirst();
	}

	List<KnowledgeVersion> listVersions(UUID tenantId, UUID documentId, int limit, int offset) {
		return jdbcTemplate.query(
			"SELECT * FROM knowledge_version WHERE tenant_id = ? AND knowledge_document_id = ? "
				+ "ORDER BY version_no DESC LIMIT ? OFFSET ?",
			(resultSet, rowNum) -> mapVersion(resultSet),
			tenantId,
			documentId,
			limit,
			offset);
	}

	int updateVersion(
			UUID tenantId,
			KnowledgeVersion previous,
			KnowledgeVersion updated,
			String updatedBy,
			OffsetDateTime now) {
		return jdbcTemplate.update(
			"UPDATE knowledge_version SET publication_status = ?, approval_checkpoint_id = ?, "
				+ "approval_checkpoint_version = ?, approved_by_user_id = ?, approved_by_display_name = ?, "
				+ "approved_at = ?, failure_code = ?, version = ?, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND knowledge_version_id = ? AND version = ?",
			updated.publicationStatus(),
			updated.approvalCheckpointRef() == null ? null : updated.approvalCheckpointRef().id(),
			updated.approvalCheckpointRef() == null ? null : updated.approvalCheckpointRef().version(),
			updated.approvedBy() == null ? null : updated.approvedBy().id(),
			updated.approvedBy() == null ? null : updated.approvedBy().displayName(),
			updated.approvedAt(), updated.failureCode(), updated.version(), now, updatedBy,
			tenantId, previous.id(), previous.version());
	}

	void disableOtherPublishedVersions(
			UUID tenantId,
			UUID documentId,
			UUID exceptVersionId,
			String updatedBy,
			OffsetDateTime now) {
		jdbcTemplate.update(
			"UPDATE knowledge_version SET publication_status = 'DISABLED', version = version + 1, "
				+ "updated_at = ?, updated_by = ? WHERE tenant_id = ? AND knowledge_document_id = ? "
				+ "AND knowledge_version_id <> ? AND publication_status = 'PUBLISHED'",
			now, updatedBy, tenantId, documentId, exceptVersionId);
	}

	void disableAllPublishedVersions(
			UUID tenantId,
			UUID documentId,
			String updatedBy,
			OffsetDateTime now) {
		jdbcTemplate.update(
			"UPDATE knowledge_version SET publication_status = 'DISABLED', version = version + 1, "
				+ "updated_at = ?, updated_by = ? WHERE tenant_id = ? AND knowledge_document_id = ? "
				+ "AND publication_status = 'PUBLISHED'",
			now, updatedBy, tenantId, documentId);
	}

	void insertUpload(UUID tenantId, StoredUpload upload, String createdBy, OffsetDateTime now) {
		jdbcTemplate.update(
			"INSERT INTO knowledge_upload_session (knowledge_upload_session_id, tenant_id, knowledge_document_id, "
				+ "status, file_name, mime_type, size_bytes, sha256, object_key, content_text, expires_at, "
				+ "completed_version_id, version, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			upload.id(), tenantId, upload.documentId(), upload.status(), upload.fileName(), upload.mimeType(),
			upload.sizeBytes(), upload.sha256(), upload.objectKey(), upload.contentText(), upload.expiresAt(),
			upload.completedVersionId(), upload.version(), now, createdBy, now, createdBy);
	}

	Optional<StoredUpload> findUpload(UUID tenantId, UUID uploadId) {
		return jdbcTemplate.query(
			"SELECT * FROM knowledge_upload_session WHERE tenant_id = ? AND knowledge_upload_session_id = ?",
			(resultSet, rowNum) -> mapUpload(resultSet),
			tenantId,
			uploadId).stream().findFirst();
	}

	int storeUploadContent(
			UUID tenantId,
			StoredUpload previous,
			String content,
			String updatedBy,
			OffsetDateTime now) {
		return jdbcTemplate.update(
			"UPDATE knowledge_upload_session SET status = 'UPLOADED', content_text = ?, "
				+ "updated_at = ?, updated_by = ? WHERE tenant_id = ? AND knowledge_upload_session_id = ? "
				+ "AND version = ? AND status = 'CREATED'",
			content, now, updatedBy, tenantId, previous.id(), previous.version());
	}

	int completeUpload(
			UUID tenantId,
			StoredUpload previous,
			UUID versionId,
			String updatedBy,
			OffsetDateTime now) {
		return jdbcTemplate.update(
			"UPDATE knowledge_upload_session SET status = 'COMPLETED', completed_version_id = ?, "
				+ "version = version + 1, updated_at = ?, updated_by = ? "
				+ "WHERE tenant_id = ? AND knowledge_upload_session_id = ? AND version = ? AND status = 'UPLOADED'",
			versionId, now, updatedBy, tenantId, previous.id(), previous.version());
	}

	Optional<ChunkView> findChunk(UUID tenantId, UUID evidenceId) {
		return jdbcTemplate.query(
			"SELECT c.*, v.knowledge_document_id, v.version, d.title, d.version AS document_version, d.classification "
				+ "FROM knowledge_chunk c "
				+ "JOIN knowledge_version v ON v.tenant_id = c.tenant_id "
				+ "AND v.knowledge_version_id = c.knowledge_version_id "
				+ "JOIN knowledge_document d ON d.tenant_id = v.tenant_id "
				+ "AND d.knowledge_document_id = v.knowledge_document_id "
				+ "WHERE c.tenant_id = ? AND c.knowledge_chunk_id = ?",
			(resultSet, rowNum) -> new ChunkView(
				resultSet.getObject("knowledge_chunk_id", UUID.class),
				resultSet.getObject("knowledge_version_id", UUID.class),
				resultSet.getObject("knowledge_document_id", UUID.class),
				resultSet.getLong("version"),
				resultSet.getLong("document_version"),
				resultSet.getString("title"),
				resultSet.getString("classification"),
				resultSet.getString("chunk_text"),
				resultSet.getString("quote_hash"),
				resultSet.getInt("chunk_no"),
				resultSet.getInt("start_offset"),
				resultSet.getInt("end_offset")),
			tenantId,
			evidenceId).stream().findFirst();
	}

	<T> Optional<StoredCommand<T>> findCommand(
			UUID tenantId,
			String operation,
			UUID idempotencyKey,
			Class<T> responseType) {
		return jdbcTemplate.query(
			"SELECT request_hash, response_json, result_version FROM knowledge_command "
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
			UUID resourceId,
			String operation,
			UUID idempotencyKey,
			String requestHash,
			Object response,
			long resultVersion,
			String createdBy,
			OffsetDateTime now) {
		jdbcTemplate.update(
			"INSERT INTO knowledge_command (knowledge_command_id, tenant_id, resource_id, operation, idempotency_key, "
				+ "request_hash, response_json, response_type, result_version, created_at, created_by, updated_at, updated_by) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			UUID.randomUUID(), tenantId, resourceId, operation, idempotencyKey, requestHash, json(response),
			response.getClass().getSimpleName(), resultVersion, now, createdBy, now, createdBy);
	}

	private KnowledgeDocument mapDocument(UUID tenantId, ResultSet resultSet) throws SQLException {
		UUID currentVersionId = resultSet.getObject("current_version_id", UUID.class);
		KnowledgeVersion currentVersion = currentVersionId == null
			? null
			: findVersion(tenantId, currentVersionId).map(VersionContent::version).orElse(null);
		return new KnowledgeDocument(
			resultSet.getObject("knowledge_document_id", UUID.class),
			resultSet.getString("title"),
			resultSet.getString("knowledge_type"),
			new ResourceRef(
				resultSet.getString("owner_resource_type"),
				resultSet.getObject("owner_resource_id", UUID.class),
				resultSet.getLong("owner_resource_version")),
			resultSet.getString("classification"),
			resultSet.getString("status"),
			resultSet.getLong("version"),
			read(resultSet.getString("tags_json"), STRING_LIST),
			resultSet.getObject("access_policy_id", UUID.class),
			resultSet.getObject("retention_until", OffsetDateTime.class),
			currentVersion,
			resultSet.getObject("created_at", OffsetDateTime.class),
			resultSet.getObject("updated_at", OffsetDateTime.class));
	}

	private VersionContent mapVersionContent(ResultSet resultSet) throws SQLException {
		return new VersionContent(
			mapVersion(resultSet),
			resultSet.getLong("size_bytes"),
			resultSet.getString("content_text"),
			resultSet.getString("change_summary"));
	}

	private KnowledgeVersion mapVersion(ResultSet resultSet) throws SQLException {
		UUID checkpointId = resultSet.getObject("approval_checkpoint_id", UUID.class);
		UUID approvedById = resultSet.getObject("approved_by_user_id", UUID.class);
		return new KnowledgeVersion(
			resultSet.getObject("knowledge_version_id", UUID.class),
			resultSet.getObject("knowledge_document_id", UUID.class),
			resultSet.getInt("version_no"),
			resultSet.getLong("version"),
			resultSet.getString("file_name"),
			resultSet.getString("mime_type"),
			resultSet.getString("sha256"),
			resultSet.getString("content_hash"),
			resultSet.getString("publication_status"),
			resultSet.getString("parse_status"),
			resultSet.getString("index_status"),
			resultSet.getString("parser_version"),
			resultSet.getObject("effective_from", OffsetDateTime.class),
			resultSet.getObject("effective_to", OffsetDateTime.class),
			checkpointId == null ? null : new ResourceRef(
				"HumanCheckpoint", checkpointId, resultSet.getLong("approval_checkpoint_version")),
			approvedById == null ? null : new UserRef(
				approvedById, resultSet.getString("approved_by_display_name")),
			resultSet.getObject("approved_at", OffsetDateTime.class),
			resultSet.getString("failure_code"),
			resultSet.getObject("created_at", OffsetDateTime.class));
	}

	private StoredUpload mapUpload(ResultSet resultSet) throws SQLException {
		return new StoredUpload(
			resultSet.getObject("knowledge_upload_session_id", UUID.class),
			resultSet.getObject("knowledge_document_id", UUID.class),
			resultSet.getString("status"),
			resultSet.getLong("version"),
			resultSet.getString("file_name"),
			resultSet.getString("mime_type"),
			resultSet.getLong("size_bytes"),
			resultSet.getString("sha256"),
			resultSet.getString("object_key"),
			resultSet.getString("content_text"),
			resultSet.getObject("expires_at", OffsetDateTime.class),
			resultSet.getObject("completed_version_id", UUID.class));
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize knowledge data", exception);
		}
	}

	private <T> T read(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to deserialize knowledge data", exception);
		}
	}

	private <T> T read(String value, TypeReference<T> type) {
		try {
			return objectMapper.readValue(value, type);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to deserialize knowledge data", exception);
		}
	}

	record StoredCommand<T>(String requestHash, T response, long resultVersion) {
	}

	record ChunkView(
			UUID id,
			UUID versionId,
			UUID documentId,
			long version,
			long documentVersion,
			String documentTitle,
			String classification,
			String text,
			String quoteHash,
			int chunkNo,
			int startOffset,
			int endOffset) {
	}
}

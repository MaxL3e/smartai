package com.smartai.core.recruitment.agent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

final class KnowledgeModels {

	private KnowledgeModels() {
	}

	record DocumentCreateRequest(
			@NotBlank @Size(max = 300) String title,
			@NotBlank @Pattern(regexp = "^(JOB_KNOWLEDGE|TALENT_PROFILE|POLICY_PROCESS|EVALUATION_STANDARD)$") String type,
			@NotNull @Valid ResourceRef ownerOrganizationRef,
			@NotBlank @Pattern(regexp = "^(INTERNAL|CONFIDENTIAL|RESTRICTED)$") String classification,
			@Size(max = 100) List<@NotBlank @Size(max = 80) String> tags,
			UUID accessPolicyId,
			OffsetDateTime retentionUntil) {
	}

	record DocumentPatchRequest(
			@Size(min = 1, max = 300) String title,
			@Pattern(regexp = "^(INTERNAL|CONFIDENTIAL|RESTRICTED)$") String classification,
			@Size(max = 100) List<@NotBlank @Size(max = 80) String> tags,
			UUID accessPolicyId,
			OffsetDateTime retentionUntil,
			@Pattern(regexp = "^(DISABLE|RESTORE|ARCHIVE)$") String statusCommand,
			@Size(max = 2000) String reason) {
	}

	record UploadSessionCreateRequest(
			@NotBlank @Size(max = 255) String fileName,
			@NotBlank @Size(max = 120) String mimeType,
			@NotNull @Positive Long sizeBytes,
			@NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String sha256) {
	}

	record UploadCompleteRequest(
			@NotNull @Positive Long sizeBytes,
			@NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String sha256,
			OffsetDateTime effectiveFrom,
			OffsetDateTime effectiveTo,
			@Size(max = 2000) String changeSummary) {
	}

	record ReasonRequest(@NotBlank @Size(max = 2000) String reason) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record KnowledgeDocument(
			UUID id,
			String title,
			String type,
			ResourceRef ownerOrganizationRef,
			String classification,
			String status,
			long version,
			List<String> tags,
			UUID accessPolicyId,
			OffsetDateTime retentionUntil,
			KnowledgeVersion currentVersion,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record KnowledgeVersion(
			UUID id,
			UUID documentId,
			int versionNo,
			long version,
			String fileName,
			String mimeType,
			String sha256,
			String contentHash,
			String publicationStatus,
			String parseStatus,
			String indexStatus,
			String parserVersion,
			OffsetDateTime effectiveFrom,
			OffsetDateTime effectiveTo,
			ResourceRef approvalCheckpointRef,
			UserRef approvedBy,
			OffsetDateTime approvedAt,
			String failureCode,
			OffsetDateTime createdAt) {
	}

	record KnowledgeUploadSession(
			UUID id,
			UUID documentId,
			String status,
			long version,
			String uploadUrl,
			Map<String, String> uploadHeaders,
			String objectKey,
			OffsetDateTime expiresAt) {
	}

	record SourceLocator(Integer page, String section, Integer paragraph, int startOffset, int endOffset) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record EvidenceRef(
			UUID id,
			String sourceType,
			ResourceRef sourceVersionRef,
			SourceLocator sourceLocator,
			String quote,
			String quoteHash,
			double extractionConfidence,
			UUID modelInvocationId,
			UserRef verifiedBy,
			OffsetDateTime verifiedAt) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record KnowledgeCitation(
			EvidenceRef evidenceRef,
			ResourceRef documentRef,
			ResourceRef knowledgeVersionRef,
			String documentTitle,
			SourceLocator sourceLocator,
			String quoteHash,
			String accessLevel,
			String quote) {
	}

	record PageResult<T>(List<T> items, boolean hasMore, String nextCursor) {
	}

	record StoredUpload(
			UUID id,
			UUID documentId,
			String status,
			long version,
			String fileName,
			String mimeType,
			long sizeBytes,
			String sha256,
			String objectKey,
			String contentText,
			OffsetDateTime expiresAt,
			UUID completedVersionId) {
	}

	record VersionContent(KnowledgeVersion version, long sizeBytes, String contentText, String changeSummary) {
	}

	record Chunk(
			UUID id,
			UUID versionId,
			int chunkNo,
			String text,
			String quoteHash,
			int startOffset,
			int endOffset) {
	}

}

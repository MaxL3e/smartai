package com.smartai.core.recruitment.agent;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartai.core.platform.api.ApiException;
import com.smartai.core.recruitment.agent.KnowledgeModels.Chunk;
import com.smartai.core.recruitment.agent.KnowledgeModels.DocumentCreateRequest;
import com.smartai.core.recruitment.agent.KnowledgeModels.DocumentPatchRequest;
import com.smartai.core.recruitment.agent.KnowledgeModels.EvidenceRef;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeCitation;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeDocument;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeUploadSession;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeVersion;
import com.smartai.core.recruitment.agent.KnowledgeModels.PageResult;
import com.smartai.core.recruitment.agent.KnowledgeModels.ReasonRequest;
import com.smartai.core.recruitment.agent.KnowledgeModels.SourceLocator;
import com.smartai.core.recruitment.agent.KnowledgeModels.StoredUpload;
import com.smartai.core.recruitment.agent.KnowledgeModels.UploadCompleteRequest;
import com.smartai.core.recruitment.agent.KnowledgeModels.UploadSessionCreateRequest;
import com.smartai.core.recruitment.agent.KnowledgeModels.VersionContent;
import com.smartai.core.recruitment.agent.PositionPlanModels.DecisionRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanCheckpoint;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanReviewRequest;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

@Service
public class KnowledgeService {

	private static final String CREATE_DOCUMENT = "CREATE_KNOWLEDGE_DOCUMENT";
	private static final String PATCH_DOCUMENT = "PATCH_KNOWLEDGE_DOCUMENT";
	private static final String CREATE_VERSION = "CREATE_KNOWLEDGE_VERSION";
	private static final String CREATE_UPLOAD = "CREATE_KNOWLEDGE_UPLOAD";
	private static final String COMPLETE_UPLOAD = "COMPLETE_KNOWLEDGE_UPLOAD";
	private static final String REQUEST_REVIEW = "REQUEST_KNOWLEDGE_REVIEW";
	private static final String DECIDE_REVIEW = "DECIDE_KNOWLEDGE_REVIEW";
	private static final String DEACTIVATE_VERSION = "DEACTIVATE_KNOWLEDGE_VERSION";
	private static final String PARSER_VERSION = "deterministic-text-v1";
	private static final long MAX_FILE_SIZE = 104_857_600L;
	private static final int MAX_CHUNK_LENGTH = 1_800;
	private static final Set<String> KNOWLEDGE_TYPES = Set.of(
		"JOB_KNOWLEDGE", "TALENT_PROFILE", "POLICY_PROCESS", "EVALUATION_STANDARD");
	private static final Set<String> DOCUMENT_STATUSES = Set.of(
		"DRAFT", "IN_REVIEW", "PUBLISHED", "DISABLED", "ARCHIVED");
	private static final Set<String> TEXT_MIME_TYPES = Set.of(
		"text/plain", "text/markdown", "application/json");
	private static final Set<String> DEFERRED_BINARY_MIME_TYPES = Set.of(
		"application/pdf",
		"application/msword",
		"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
		"application/vnd.ms-excel",
		"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
		"application/vnd.ms-powerpoint",
		"application/vnd.openxmlformats-officedocument.presentationml.presentation");

	private final KnowledgeRepository repository;
	private final PositionPlanRepository sharedRepository;
	private final KnowledgeHasher hasher;

	KnowledgeService(
			KnowledgeRepository repository,
			PositionPlanRepository sharedRepository,
			KnowledgeHasher hasher) {
		this.repository = repository;
		this.sharedRepository = sharedRepository;
		this.hasher = hasher;
	}

	@Transactional
	CommandResult<KnowledgeDocument> createDocument(
			TenantActor actor,
			UUID idempotencyKey,
			DocumentCreateRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(CREATE_DOCUMENT, null, null, request);
		var previous = repository.findCommand(
			actor.tenantId(), CREATE_DOCUMENT, idempotencyKey, KnowledgeDocument.class);
		if (previous.isPresent()) {
			verifyIdempotency(previous.get().requestHash(), requestHash);
			return new CommandResult<>(previous.get().response(), true);
		}
		validateOwnerReference(request.ownerOrganizationRef());
		List<String> tags = normalizeTags(request.tags());
		OffsetDateTime now = now();
		KnowledgeDocument document = new KnowledgeDocument(
			UUID.randomUUID(),
			request.title().strip(),
			request.type(),
			request.ownerOrganizationRef(),
			request.classification(),
			"DRAFT",
			1L,
			tags,
			request.accessPolicyId(),
			request.retentionUntil(),
			null,
			now,
			now);
		repository.insertDocument(actor.tenantId(), document, actor.userId().toString());
		repository.insertCommand(
			actor.tenantId(), document.id(), CREATE_DOCUMENT, idempotencyKey, requestHash, document,
			document.version(), actor.userId().toString(), now);
		audit(actor, "KnowledgeDocument", document.id(), "KNOWLEDGE_DOCUMENT_CREATED",
			Map.of("knowledgeType", document.type(), "classification", document.classification()), audit, now);
		return new CommandResult<>(document, false);
	}

	@Transactional(readOnly = true)
	KnowledgeDocument getDocument(TenantActor actor, UUID documentId) {
		return requireDocument(actor, documentId);
	}

	@Transactional(readOnly = true)
	PageResult<KnowledgeDocument> listDocuments(
			TenantActor actor,
			String type,
			String status,
			String cursor,
			int limit) {
		validateLimit(limit);
		if (type != null && !KNOWLEDGE_TYPES.contains(type)) throw validation("Unsupported knowledge type");
		if (status != null && !DOCUMENT_STATUSES.contains(status)) throw validation("Unsupported knowledge status");
		int offset = decodeCursor(cursor);
		List<KnowledgeDocument> page = repository.listDocuments(actor.tenantId(), type, status, limit + 1, offset);
		boolean hasMore = page.size() > limit;
		List<KnowledgeDocument> items = hasMore ? page.subList(0, limit) : page;
		return new PageResult<>(items, hasMore, hasMore ? encodeCursor(offset + limit) : null);
	}

	@Transactional
	CommandResult<KnowledgeDocument> patchDocument(
			TenantActor actor,
			UUID documentId,
			long expectedVersion,
			UUID idempotencyKey,
			DocumentPatchRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(PATCH_DOCUMENT, documentId, expectedVersion, request);
		var previousCommand = repository.findCommand(
			actor.tenantId(), PATCH_DOCUMENT, idempotencyKey, KnowledgeDocument.class);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}
		KnowledgeDocument current = requireDocument(actor, documentId);
		VersionPrecondition.verify(expectedVersion, current.version());
		if ("ARCHIVED".equals(current.status())
				&& !"RESTORE".equals(request.statusCommand())) {
			throw conflict("KNOWLEDGE_DOCUMENT_ARCHIVED", "Archived knowledge cannot be changed");
		}
		List<String> tags = request.tags() == null ? current.tags() : normalizeTags(request.tags());
		String title = request.title() == null ? current.title() : request.title().strip();
		String classification = request.classification() == null ? current.classification() : request.classification();
		String status = current.status();
		KnowledgeVersion currentVersion = current.currentVersion();
		OffsetDateTime now = now();
		if (request.statusCommand() != null) {
			if (request.reason() == null || request.reason().isBlank()) {
				throw validation("reason is required for a status command");
			}
			switch (request.statusCommand()) {
				case "DISABLE" -> {
					if (!"PUBLISHED".equals(current.status())) {
						throw conflict("KNOWLEDGE_STATE_CONFLICT", "Only published knowledge can be disabled");
					}
					status = "DISABLED";
					if (currentVersion != null && "PUBLISHED".equals(currentVersion.publicationStatus())) {
						KnowledgeVersion disabled = withVersionState(
							currentVersion, "DISABLED", currentVersion.version() + 1,
							currentVersion.approvalCheckpointRef(), currentVersion.approvedBy(),
							currentVersion.approvedAt());
						updateVersion(actor, currentVersion, disabled, now);
						currentVersion = disabled;
					}
				}
				case "RESTORE" -> {
					if (!Set.of("DISABLED", "ARCHIVED").contains(current.status())) {
						throw conflict("KNOWLEDGE_STATE_CONFLICT", "Only disabled or archived knowledge can be restored");
					}
					if (currentVersion == null) {
						status = "DRAFT";
					}
					else if ("DISABLED".equals(currentVersion.publicationStatus())
							&& currentVersion.approvalCheckpointRef() != null
							&& currentVersion.approvedAt() != null) {
						KnowledgeVersion restored = withVersionState(
							currentVersion, "PUBLISHED", currentVersion.version() + 1,
							currentVersion.approvalCheckpointRef(), currentVersion.approvedBy(),
							currentVersion.approvedAt());
						updateVersion(actor, currentVersion, restored, now);
						currentVersion = restored;
						status = "PUBLISHED";
					}
					else {
						status = "DRAFT";
					}
				}
				case "ARCHIVE" -> {
					if ("IN_REVIEW".equals(current.status())) {
						throw conflict("KNOWLEDGE_STATE_CONFLICT", "Knowledge in review cannot be archived");
					}
					repository.disableAllPublishedVersions(
						actor.tenantId(), current.id(), actor.userId().toString(), now);
					if (currentVersion != null && "PUBLISHED".equals(currentVersion.publicationStatus())) {
						currentVersion = withVersionState(
							currentVersion, "DISABLED", currentVersion.version() + 1,
							currentVersion.approvalCheckpointRef(), currentVersion.approvedBy(),
							currentVersion.approvedAt());
					}
					status = "ARCHIVED";
				}
				default -> throw validation("Unsupported status command");
			}
		}
		KnowledgeDocument updated = new KnowledgeDocument(
			current.id(), title, current.type(), current.ownerOrganizationRef(), classification, status,
			current.version() + 1, tags,
			request.accessPolicyId() == null ? current.accessPolicyId() : request.accessPolicyId(),
			request.retentionUntil() == null ? current.retentionUntil() : request.retentionUntil(),
			currentVersion, current.createdAt(), now);
		updateDocument(actor, current, updated);
		repository.insertCommand(
			actor.tenantId(), documentId, PATCH_DOCUMENT, idempotencyKey, requestHash, updated, updated.version(),
			actor.userId().toString(), now);
		audit(actor, "KnowledgeDocument", documentId, "KNOWLEDGE_DOCUMENT_UPDATED",
			Map.of(
				"beforeVersion", current.version(),
				"afterVersion", updated.version(),
				"statusCommand", request.statusCommand() == null ? "NONE" : request.statusCommand()),
			audit, now);
		return new CommandResult<>(updated, false);
	}

	@Transactional
	CommandResult<KnowledgeVersion> createVersion(
			TenantActor actor,
			UUID documentId,
			UUID idempotencyKey,
			String fileName,
			String mimeType,
			byte[] bytes,
			String sha256,
			OffsetDateTime effectiveFrom,
			OffsetDateTime effectiveTo,
			String changeSummary,
			AuditContext audit) {
		String requestHash = hasher.commandHash(
			CREATE_VERSION, documentId, null,
			Map.of(
				"fileName", fileName,
				"mimeType", mimeType,
				"sha256", sha256,
				"effectiveFrom", nullable(effectiveFrom),
				"effectiveTo", nullable(effectiveTo),
				"changeSummary", nullable(changeSummary)));
		var previous = repository.findCommand(
			actor.tenantId(), CREATE_VERSION, idempotencyKey, KnowledgeVersion.class);
		if (previous.isPresent()) {
			verifyIdempotency(previous.get().requestHash(), requestHash);
			return new CommandResult<>(previous.get().response(), true);
		}
		ParsedFile parsed = parseFile(fileName, mimeType, bytes, sha256);
		validateEffectiveDates(effectiveFrom, effectiveTo);
		KnowledgeDocument document = lockDocument(actor, documentId);
		KnowledgeVersion version = createParsedVersion(
			actor, document, parsed, effectiveFrom, effectiveTo, changeSummary, audit);
		OffsetDateTime now = version.createdAt();
		repository.insertCommand(
			actor.tenantId(), version.id(), CREATE_VERSION, idempotencyKey, requestHash, version, version.version(),
			actor.userId().toString(), now);
		return new CommandResult<>(version, false);
	}

	@Transactional(readOnly = true)
	KnowledgeVersion getVersion(TenantActor actor, UUID versionId) {
		return requireVersion(actor, versionId).version();
	}

	@Transactional(readOnly = true)
	PageResult<KnowledgeVersion> listVersions(
			TenantActor actor,
			UUID documentId,
			String cursor,
			int limit) {
		validateLimit(limit);
		requireDocument(actor, documentId);
		int offset = decodeCursor(cursor);
		List<KnowledgeVersion> page = repository.listVersions(
			actor.tenantId(), documentId, limit + 1, offset);
		boolean hasMore = page.size() > limit;
		List<KnowledgeVersion> items = hasMore ? page.subList(0, limit) : page;
		return new PageResult<>(items, hasMore, hasMore ? encodeCursor(offset + limit) : null);
	}

	@Transactional
	CommandResult<KnowledgeUploadSession> createUploadSession(
			TenantActor actor,
			UUID documentId,
			UUID idempotencyKey,
			UploadSessionCreateRequest request,
			AuditContext audit) {
		validateFileMetadata(request.fileName(), request.mimeType(), request.sizeBytes());
		String requestHash = hasher.commandHash(CREATE_UPLOAD, documentId, null, request);
		var previous = repository.findCommand(
			actor.tenantId(), CREATE_UPLOAD, idempotencyKey, KnowledgeUploadSession.class);
		if (previous.isPresent()) {
			verifyIdempotency(previous.get().requestHash(), requestHash);
			return new CommandResult<>(previous.get().response(), true);
		}
		KnowledgeDocument document = requireDocument(actor, documentId);
		ensureDocumentAcceptsVersion(document);
		OffsetDateTime now = now();
		UUID id = UUID.randomUUID();
		String objectKey = actor.tenantId() + "/knowledge/" + documentId + "/" + id;
		StoredUpload stored = new StoredUpload(
			id, documentId, "CREATED", 1L, request.fileName().strip(), normalizeMime(request.mimeType()),
			request.sizeBytes(), request.sha256(), objectKey, null, now.plusMinutes(15), null);
		repository.insertUpload(actor.tenantId(), stored, actor.userId().toString(), now);
		KnowledgeUploadSession session = uploadView(stored);
		repository.insertCommand(
			actor.tenantId(), id, CREATE_UPLOAD, idempotencyKey, requestHash, session, session.version(),
			actor.userId().toString(), now);
		audit(actor, "KnowledgeUploadSession", id, "KNOWLEDGE_UPLOAD_SESSION_CREATED",
			Map.of("documentId", documentId, "sizeBytes", request.sizeBytes()), audit, now);
		return new CommandResult<>(session, false);
	}

	@Transactional
	KnowledgeUploadSession storeUpload(
			TenantActor actor,
			UUID uploadId,
			byte[] bytes,
			String contentType,
			String sha256Header) {
		StoredUpload upload = requireUpload(actor, uploadId);
		if (!"CREATED".equals(upload.status())) {
			throw conflict("UPLOAD_STATE_CONFLICT", "Upload session is not writable");
		}
		OffsetDateTime now = now();
		if (!upload.expiresAt().isAfter(now)) {
			throw new ApiException(HttpStatus.GONE, "UPLOAD_SESSION_EXPIRED", "Upload session has expired");
		}
		if (bytes.length != upload.sizeBytes()) {
			throw validation("Uploaded content size does not match the declared size");
		}
		String actualHash = hasher.bytesHash(bytes);
		if (!secureEquals(upload.sha256(), actualHash)
				|| !secureEquals(upload.sha256(), sha256Header)) {
			throw validation("Uploaded content SHA-256 does not match the upload session");
		}
		if (!normalizeMime(contentType).equals(upload.mimeType())) {
			throw validation("Uploaded Content-Type does not match the upload session");
		}
		String content = storedPayload(upload.mimeType(), bytes);
		if (repository.storeUploadContent(
				actor.tenantId(), upload, content, actor.userId().toString(), now) != 1) {
			throw versionConflict();
		}
		return uploadView(new StoredUpload(
			upload.id(), upload.documentId(), "UPLOADED", upload.version(), upload.fileName(),
			upload.mimeType(), upload.sizeBytes(), upload.sha256(), upload.objectKey(), content,
			upload.expiresAt(), null));
	}

	@Transactional
	CommandResult<KnowledgeVersion> completeUpload(
			TenantActor actor,
			UUID uploadId,
			long expectedVersion,
			UUID idempotencyKey,
			UploadCompleteRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(COMPLETE_UPLOAD, uploadId, expectedVersion, request);
		var previousCommand = repository.findCommand(
			actor.tenantId(), COMPLETE_UPLOAD, idempotencyKey, KnowledgeVersion.class);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}
		StoredUpload upload = requireUpload(actor, uploadId);
		if ("COMPLETED".equals(upload.status()) && upload.completedVersionId() != null) {
			return new CommandResult<>(requireVersion(actor, upload.completedVersionId()).version(), true);
		}
		VersionPrecondition.verify(expectedVersion, upload.version());
		if (!"UPLOADED".equals(upload.status())) {
			throw conflict("UPLOAD_STATE_CONFLICT", "Upload content must be stored before completion");
		}
		if (!upload.expiresAt().isAfter(now())) {
			throw new ApiException(HttpStatus.GONE, "UPLOAD_SESSION_EXPIRED", "Upload session has expired");
		}
		if (request.sizeBytes() != upload.sizeBytes()
				|| !secureEquals(request.sha256(), upload.sha256())) {
			throw validation("Completion metadata does not match the upload session");
		}
		validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());
		KnowledgeDocument document = lockDocument(actor, upload.documentId());
		ParsedFile parsed = parsedUpload(upload);
		KnowledgeVersion version = createParsedVersion(
			actor, document, parsed, request.effectiveFrom(), request.effectiveTo(),
			request.changeSummary(), audit);
		OffsetDateTime now = version.createdAt();
		if (repository.completeUpload(
				actor.tenantId(), upload, version.id(), actor.userId().toString(), now) != 1) {
			throw versionConflict();
		}
		repository.insertCommand(
			actor.tenantId(), uploadId, COMPLETE_UPLOAD, idempotencyKey, requestHash, version, version.version(),
			actor.userId().toString(), now);
		return new CommandResult<>(version, false);
	}

	@Transactional
	CommandResult<HumanCheckpoint> requestReview(
			TenantActor actor,
			UUID versionId,
			long expectedVersion,
			UUID idempotencyKey,
			HumanReviewRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(REQUEST_REVIEW, versionId, expectedVersion, request);
		var previousCommand = repository.findCommand(
			actor.tenantId(), REQUEST_REVIEW, idempotencyKey, HumanCheckpoint.class);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}
		VersionContent content = requireVersion(actor, versionId);
		KnowledgeVersion current = content.version();
		VersionPrecondition.verify(expectedVersion, current.version());
		if (!"DRAFT".equals(current.publicationStatus())
				|| !"PARSED".equals(current.parseStatus())
				|| !"INDEXED".equals(current.indexStatus())) {
			throw conflict("KNOWLEDGE_VERSION_STATE_CONFLICT", "Only parsed and indexed draft versions can be reviewed");
		}
		if (!"KNOWLEDGE_ADMIN".equals(request.requiredRole())) {
			throw validation("Knowledge publication review requires KNOWLEDGE_ADMIN");
		}
		if (!secureEquals(current.contentHash(), request.inputHash())) {
			throw conflict("CONFIRMATION_INPUT_CHANGED", "Review input does not match the knowledge content");
		}
		OffsetDateTime now = now();
		if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
			throw validation("expiresAt must be in the future");
		}
		KnowledgeDocument document = requireDocument(actor, current.documentId());
		if (document.currentVersion() == null || !document.currentVersion().id().equals(current.id())) {
			throw conflict("KNOWLEDGE_VERSION_NOT_CURRENT", "Only the current knowledge version can be reviewed");
		}
		UUID checkpointId = UUID.randomUUID();
		KnowledgeVersion inReview = withVersionState(
			current, "IN_REVIEW", current.version() + 1,
			new ResourceRef("HumanCheckpoint", checkpointId, 1L), null, null);
		HumanCheckpoint checkpoint = new HumanCheckpoint(
			checkpointId,
			null,
			"PUBLISH_KNOWLEDGE",
			new ResourceRef("KnowledgeVersion", inReview.id(), inReview.version()),
			"PENDING",
			"KNOWLEDGE_ADMIN",
			request.assigneeUserId(),
			current.contentHash(),
			1L,
			"Review the parsed content, effective dates and classification before publication.",
			user(actor),
			now,
			request.expiresAt(),
			null,
			request.comment(),
			null,
			null);
		updateVersion(actor, current, inReview, now);
		KnowledgeDocument updatedDocument = withDocumentState(
			document, "IN_REVIEW", document.version() + 1, inReview, now);
		updateDocument(actor, document, updatedDocument);
		sharedRepository.insertCheckpoint(actor.tenantId(), checkpoint, null, actor.userId().toString());
		repository.insertCommand(
			actor.tenantId(), versionId, REQUEST_REVIEW, idempotencyKey, requestHash, checkpoint,
			checkpoint.version(), actor.userId().toString(), now);
		audit(actor, "HumanCheckpoint", checkpointId, "KNOWLEDGE_REVIEW_REQUESTED",
			Map.of("documentId", document.id(), "knowledgeVersionId", versionId, "versionNo", current.versionNo()),
			audit, now);
		return new CommandResult<>(checkpoint, false);
	}

	@Transactional
	CommandResult<HumanCheckpoint> decideReview(
			TenantActor actor,
			UUID checkpointId,
			long expectedVersion,
			UUID idempotencyKey,
			DecisionRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(DECIDE_REVIEW, checkpointId, expectedVersion, request);
		var previousCommand = repository.findCommand(
			actor.tenantId(), DECIDE_REVIEW, idempotencyKey, HumanCheckpoint.class);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}
		HumanCheckpoint current = requireCheckpoint(actor, checkpointId);
		if (!"PUBLISH_KNOWLEDGE".equals(current.type())) {
			throw conflict("CHECKPOINT_TYPE_MISMATCH", "Checkpoint is not a knowledge publication gate");
		}
		VersionPrecondition.verify(expectedVersion, current.version());
		if (!"PENDING".equals(current.status())) {
			throw conflict("CHECKPOINT_ALREADY_DECIDED", "Checkpoint is already terminal");
		}
		OffsetDateTime now = now();
		if (current.expiresAt() != null && !current.expiresAt().isAfter(now)) {
			throw new ApiException(HttpStatus.GONE, "CHECKPOINT_EXPIRED", "Checkpoint has expired");
		}
		if (!secureEquals(current.inputHash(), request.inputHash())) {
			throw conflict("CONFIRMATION_INPUT_CHANGED", "Decision input does not match the frozen content");
		}
		VersionContent content = requireVersion(actor, current.resourceRef().id());
		KnowledgeVersion version = content.version();
		if (version.version() != current.resourceRef().version()
				|| !"IN_REVIEW".equals(version.publicationStatus())
				|| !secureEquals(version.contentHash(), current.inputHash())) {
			throw conflict("CHECKPOINT_RESOURCE_CHANGED", "Frozen knowledge no longer matches the checkpoint");
		}
		KnowledgeDocument document = requireDocument(actor, version.documentId());
		String checkpointStatus = switch (request.decision()) {
			case "APPROVE" -> "APPROVED";
			case "REJECT" -> "REJECTED";
			case "CANCEL" -> "CANCELLED";
			default -> throw validation("Unsupported checkpoint decision");
		};
		HumanCheckpoint decided = new HumanCheckpoint(
			current.id(), null, current.type(), current.resourceRef(), checkpointStatus, current.requiredRole(),
			current.assigneeUserId(), current.inputHash(), current.version() + 1, current.summary(),
			current.requestedBy(), current.requestedAt(), current.expiresAt(), request.decision(), request.comment(),
			user(actor), now);
		boolean approved = "APPROVE".equals(request.decision());
		KnowledgeVersion decidedVersion = withVersionState(
			version,
			approved ? "PUBLISHED" : "DRAFT",
			version.version() + 1,
			approved ? new ResourceRef("HumanCheckpoint", checkpointId, decided.version()) : null,
			approved ? user(actor) : null,
			approved ? now : null);
		if (approved) {
			repository.disableOtherPublishedVersions(
				actor.tenantId(), document.id(), version.id(), actor.userId().toString(), now);
		}
		updateVersion(actor, version, decidedVersion, now);
		KnowledgeDocument updatedDocument = withDocumentState(
			document, approved ? "PUBLISHED" : "DRAFT", document.version() + 1, decidedVersion, now);
		updateDocument(actor, document, updatedDocument);
		if (sharedRepository.updateCheckpoint(
				actor.tenantId(), current, decided, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		repository.insertCommand(
			actor.tenantId(), checkpointId, DECIDE_REVIEW, idempotencyKey, requestHash, decided,
			decided.version(), actor.userId().toString(), now);
		audit(actor, "HumanCheckpoint", checkpointId, "KNOWLEDGE_REVIEW_DECIDED",
			Map.of(
				"documentId", document.id(),
				"knowledgeVersionId", version.id(),
				"decision", request.decision(),
				"publicationStatus", decidedVersion.publicationStatus()),
			audit, now);
		return new CommandResult<>(decided, false);
	}

	@Transactional
	CommandResult<KnowledgeVersion> deactivateVersion(
			TenantActor actor,
			UUID versionId,
			long expectedVersion,
			UUID idempotencyKey,
			ReasonRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(DEACTIVATE_VERSION, versionId, expectedVersion, request);
		var previousCommand = repository.findCommand(
			actor.tenantId(), DEACTIVATE_VERSION, idempotencyKey, KnowledgeVersion.class);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}
		KnowledgeVersion current = requireVersion(actor, versionId).version();
		VersionPrecondition.verify(expectedVersion, current.version());
		if (!"PUBLISHED".equals(current.publicationStatus())) {
			throw conflict("KNOWLEDGE_VERSION_STATE_CONFLICT", "Only published knowledge can be deactivated");
		}
		OffsetDateTime now = now();
		KnowledgeVersion disabled = withVersionState(
			current, "DISABLED", current.version() + 1, current.approvalCheckpointRef(),
			current.approvedBy(), current.approvedAt());
		updateVersion(actor, current, disabled, now);
		KnowledgeDocument document = requireDocument(actor, current.documentId());
		if (document.currentVersion() != null && document.currentVersion().id().equals(current.id())) {
			KnowledgeDocument updatedDocument = withDocumentState(
				document, "DISABLED", document.version() + 1, disabled, now);
			updateDocument(actor, document, updatedDocument);
		}
		repository.insertCommand(
			actor.tenantId(), versionId, DEACTIVATE_VERSION, idempotencyKey, requestHash, disabled,
			disabled.version(), actor.userId().toString(), now);
		audit(actor, "KnowledgeVersion", versionId, "KNOWLEDGE_VERSION_DEACTIVATED",
			Map.of("documentId", current.documentId(), "reason", request.reason()), audit, now);
		return new CommandResult<>(disabled, false);
	}

	@Transactional(readOnly = true)
	KnowledgeCitation resolveEvidence(TenantActor actor, UUID evidenceId) {
		KnowledgeRepository.ChunkView chunk = repository.findChunk(actor.tenantId(), evidenceId)
			.orElseThrow(this::notFound);
		boolean fullText = !"RESTRICTED".equals(chunk.classification());
		SourceLocator locator = new SourceLocator(
			null, "Imported content", chunk.chunkNo(), chunk.startOffset(), chunk.endOffset());
		ResourceRef versionRef = new ResourceRef("KnowledgeVersion", chunk.versionId(), chunk.version());
		EvidenceRef evidence = new EvidenceRef(
			chunk.id(), "KNOWLEDGE", versionRef, locator, fullText ? chunk.text() : null,
			chunk.quoteHash(), 1.0d, null, null, null);
		return new KnowledgeCitation(
			evidence,
			new ResourceRef("KnowledgeDocument", chunk.documentId(), chunk.documentVersion()),
			versionRef,
			chunk.documentTitle(),
			locator,
			chunk.quoteHash(),
			fullText ? "FULL_TEXT" : "METADATA_ONLY",
			fullText ? chunk.text() : null);
	}

	HumanCheckpoint getCheckpoint(TenantActor actor, UUID checkpointId) {
		return requireCheckpoint(actor, checkpointId);
	}

	private KnowledgeVersion createParsedVersion(
			TenantActor actor,
			KnowledgeDocument document,
			ParsedFile parsed,
			OffsetDateTime effectiveFrom,
			OffsetDateTime effectiveTo,
			String changeSummary,
			AuditContext audit) {
		ensureDocumentAcceptsVersion(document);
		OffsetDateTime now = now();
		UUID versionId = UUID.randomUUID();
		String contentHash = parsed.contentHash();
		KnowledgeVersion version = new KnowledgeVersion(
			versionId, document.id(), repository.nextVersionNo(actor.tenantId(), document.id()), 1L,
			parsed.fileName(), parsed.mimeType(), parsed.sha256(), contentHash, "DRAFT",
			parsed.parseStatus(), parsed.indexStatus(), parsed.parserVersion(),
			effectiveFrom, effectiveTo, null, null, null, parsed.failureCode(), now);
		List<Chunk> chunks = "PARSED".equals(parsed.parseStatus())
			? chunks(versionId, parsed.payload())
			: List.of();
		repository.insertVersion(
			actor.tenantId(),
			new VersionContent(version, parsed.sizeBytes(), parsed.payload(), changeSummary),
			chunks,
			actor.userId().toString(),
			now);
		KnowledgeDocument updatedDocument = withDocumentState(
			document, "DRAFT", document.version() + 1, version, now);
		updateDocument(actor, document, updatedDocument);
		audit(actor, "KnowledgeVersion", version.id(), "KNOWLEDGE_VERSION_ACCEPTED",
			Map.of(
				"documentId", document.id(),
				"versionNo", version.versionNo(),
				"parseStatus", version.parseStatus(),
				"indexStatus", version.indexStatus(),
				"chunkCount", chunks.size()),
			audit, now);
		return version;
	}

	private ParsedFile parseFile(
			String fileName,
			String mimeType,
			byte[] bytes,
			String expectedSha256) {
		validateFileMetadata(fileName, mimeType, bytes.length);
		String actualHash = hasher.bytesHash(bytes);
		if (!secureEquals(expectedSha256, actualHash)) {
			throw validation("sha256 does not match the uploaded file");
		}
		String normalizedMime = normalizeMime(mimeType);
		if (TEXT_MIME_TYPES.contains(normalizedMime)) {
			String content = normalizeText(decodeUtf8(bytes));
			return new ParsedFile(
				fileName.strip(), normalizedMime, bytes.length, actualHash, content,
				hasher.textHash(content), "PARSED", "INDEXED", PARSER_VERSION, null);
		}
		return new ParsedFile(
			fileName.strip(), normalizedMime, bytes.length, actualHash, binaryPayload(bytes),
			actualHash, "PARSE_FAILED", "NOT_INDEXED", null, "PARSER_NOT_CONFIGURED");
	}

	private void validateFileMetadata(String fileName, String mimeType, long sizeBytes) {
		if (fileName == null || fileName.isBlank() || fileName.length() > 255) {
			throw validation("fileName must contain 1 to 255 characters");
		}
		if (sizeBytes < 1 || sizeBytes > MAX_FILE_SIZE) {
			throw validation("Knowledge files must contain 1 to 104857600 bytes");
		}
		String normalizedMime = normalizeMime(mimeType);
		if (!TEXT_MIME_TYPES.contains(normalizedMime)
				&& !DEFERRED_BINARY_MIME_TYPES.contains(normalizedMime)) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"KNOWLEDGE_FILE_TYPE_UNSUPPORTED",
				"Supported knowledge files are text, Markdown, JSON, PDF, Word, Excel and PowerPoint");
		}
	}

	private ParsedFile parsedUpload(StoredUpload upload) {
		if (TEXT_MIME_TYPES.contains(upload.mimeType())) {
			String content = normalizeText(upload.contentText());
			return new ParsedFile(
				upload.fileName(), upload.mimeType(), upload.sizeBytes(), upload.sha256(), content,
				hasher.textHash(content), "PARSED", "INDEXED", PARSER_VERSION, null);
		}
		return new ParsedFile(
			upload.fileName(), upload.mimeType(), upload.sizeBytes(), upload.sha256(), upload.contentText(),
			upload.sha256(), "PARSE_FAILED", "NOT_INDEXED", null, "PARSER_NOT_CONFIGURED");
	}

	private static String storedPayload(String mimeType, byte[] bytes) {
		return TEXT_MIME_TYPES.contains(mimeType)
			? normalizeText(decodeUtf8(bytes))
			: binaryPayload(bytes);
	}

	private static String binaryPayload(byte[] bytes) {
		return "base64:" + Base64.getEncoder().encodeToString(bytes);
	}

	private static String decodeUtf8(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
		}
		catch (CharacterCodingException exception) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"KNOWLEDGE_PARSE_FAILED",
				"Knowledge text must be valid UTF-8");
		}
	}

	private List<Chunk> chunks(UUID versionId, String content) {
		List<Chunk> result = new ArrayList<>();
		int start = 0;
		int number = 1;
		while (start < content.length()) {
			int end = Math.min(start + MAX_CHUNK_LENGTH, content.length());
			if (end < content.length()) {
				int lineBreak = content.lastIndexOf('\n', end);
				if (lineBreak > start + MAX_CHUNK_LENGTH / 2) end = lineBreak + 1;
			}
			String text = content.substring(start, end);
			if (!text.isBlank()) {
				result.add(new Chunk(
					UUID.randomUUID(), versionId, number++, text, hasher.textHash(text), start, end));
			}
			start = end;
		}
		if (result.isEmpty()) throw validation("Knowledge file must contain non-whitespace text");
		return result;
	}

	private KnowledgeDocument requireDocument(TenantActor actor, UUID documentId) {
		return repository.findDocument(actor.tenantId(), documentId).orElseThrow(this::notFound);
	}

	private KnowledgeDocument lockDocument(TenantActor actor, UUID documentId) {
		return repository.lockDocument(actor.tenantId(), documentId).orElseThrow(this::notFound);
	}

	private VersionContent requireVersion(TenantActor actor, UUID versionId) {
		return repository.findVersion(actor.tenantId(), versionId).orElseThrow(this::notFound);
	}

	private StoredUpload requireUpload(TenantActor actor, UUID uploadId) {
		return repository.findUpload(actor.tenantId(), uploadId).orElseThrow(this::notFound);
	}

	private HumanCheckpoint requireCheckpoint(TenantActor actor, UUID checkpointId) {
		return sharedRepository.findCheckpoint(actor.tenantId(), checkpointId).orElseThrow(this::notFound);
	}

	private void updateDocument(
			TenantActor actor,
			KnowledgeDocument previous,
			KnowledgeDocument updated) {
		if (repository.updateDocument(actor.tenantId(), previous, updated, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
	}

	private void updateVersion(
			TenantActor actor,
			KnowledgeVersion previous,
			KnowledgeVersion updated,
			OffsetDateTime now) {
		if (repository.updateVersion(
				actor.tenantId(), previous, updated, actor.userId().toString(), now) != 1) {
			throw versionConflict();
		}
	}

	private void audit(
			TenantActor actor,
			String resourceType,
			UUID resourceId,
			String action,
			Object payload,
			AuditContext audit,
			OffsetDateTime now) {
		sharedRepository.appendAudit(
			actor.tenantId(), user(actor), resourceType, resourceId, action,
			audit.requestId(), audit.traceId(), payload, now);
	}

	private static KnowledgeDocument withDocumentState(
			KnowledgeDocument document,
			String status,
			long version,
			KnowledgeVersion currentVersion,
			OffsetDateTime now) {
		return new KnowledgeDocument(
			document.id(), document.title(), document.type(), document.ownerOrganizationRef(),
			document.classification(), status, version, document.tags(), document.accessPolicyId(),
			document.retentionUntil(), currentVersion, document.createdAt(), now);
	}

	private static KnowledgeVersion withVersionState(
			KnowledgeVersion version,
			String status,
			long resourceVersion,
			ResourceRef checkpointRef,
			UserRef approvedBy,
			OffsetDateTime approvedAt) {
		return new KnowledgeVersion(
			version.id(), version.documentId(), version.versionNo(), resourceVersion, version.fileName(),
			version.mimeType(), version.sha256(), version.contentHash(), status, version.parseStatus(),
			version.indexStatus(), version.parserVersion(), version.effectiveFrom(), version.effectiveTo(),
			checkpointRef, approvedBy, approvedAt, version.failureCode(), version.createdAt());
	}

	private static KnowledgeUploadSession uploadView(StoredUpload upload) {
		return new KnowledgeUploadSession(
			upload.id(), upload.documentId(), upload.status(), upload.version(),
			"/api/core/v1/knowledge-upload-sessions/" + upload.id() + "/content",
			Map.of(
				"Content-Type", upload.mimeType(),
				"X-Content-SHA256", upload.sha256()),
			upload.objectKey(),
			upload.expiresAt());
	}

	private static List<String> normalizeTags(List<String> tags) {
		if (tags == null) return List.of();
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		for (String tag : tags) {
			String value = tag.strip();
			if (!normalized.add(value)) throw validation("tags must be unique");
		}
		return List.copyOf(normalized);
	}

	private static void validateOwnerReference(ResourceRef reference) {
		if (reference.id() == null || reference.type() == null || reference.type().isBlank()
				|| !reference.type().matches("^[A-Z][A-Za-z0-9]{1,79}$")
				|| reference.version() < 1) {
			throw validation("ownerOrganizationRef must be a valid versioned resource reference");
		}
	}

	private static void validateEffectiveDates(OffsetDateTime from, OffsetDateTime to) {
		if (from != null && to != null && !to.isAfter(from)) {
			throw validation("effectiveTo must be after effectiveFrom");
		}
	}

	private static void ensureDocumentAcceptsVersion(KnowledgeDocument document) {
		if ("ARCHIVED".equals(document.status())) {
			throw conflict("KNOWLEDGE_DOCUMENT_ARCHIVED", "Archived knowledge cannot receive new versions");
		}
		if ("IN_REVIEW".equals(document.status())) {
			throw conflict("KNOWLEDGE_STATE_CONFLICT", "Complete the current review before adding a version");
		}
	}

	private static void validateLimit(int limit) {
		if (limit < 1 || limit > 200) throw validation("limit must be between 1 and 200");
	}

	private static int decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) return 0;
		try {
			String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
			int offset = Integer.parseInt(value);
			if (offset < 0) throw new NumberFormatException();
			return offset;
		}
		catch (IllegalArgumentException exception) {
			throw validation("cursor is invalid");
		}
	}

	private static String encodeCursor(int offset) {
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(Integer.toString(offset).getBytes(StandardCharsets.US_ASCII));
	}

	private static String normalizeMime(String mimeType) {
		if (mimeType == null) throw validation("mimeType is required");
		return mimeType.split(";", 2)[0].strip().toLowerCase();
	}

	private static String normalizeText(String text) {
		return text.replace("\r\n", "\n").replace('\r', '\n');
	}

	private static Object nullable(Object value) {
		return value == null ? "" : value;
	}

	private static UserRef user(TenantActor actor) {
		return new UserRef(actor.userId(), actor.displayName());
	}

	private static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

	private static boolean secureEquals(String first, String second) {
		if (first == null || second == null) return false;
		return MessageDigest.isEqual(
			first.getBytes(StandardCharsets.US_ASCII),
			second.getBytes(StandardCharsets.US_ASCII));
	}

	private static void verifyIdempotency(String storedHash, String requestHash) {
		if (!secureEquals(storedHash, requestHash)) {
			throw conflict("IDEMPOTENCY_CONFLICT", "Idempotency key was already used with different input");
		}
	}

	private static ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
	}

	private static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	private static ApiException versionConflict() {
		return conflict("VERSION_CONFLICT", "Knowledge resource changed concurrently");
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Knowledge resource was not found");
	}

	record AuditContext(UUID requestId, String traceId) {
	}

	record CommandResult<T>(T value, boolean replayed) {
	}

	private record ParsedFile(
			String fileName,
			String mimeType,
			long sizeBytes,
			String sha256,
			String payload,
			String contentHash,
			String parseStatus,
			String indexStatus,
			String parserVersion,
			String failureCode) {
	}
}

package com.smartai.core.recruitment.agent;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartai.core.platform.api.ApiEnvelope;
import com.smartai.core.platform.api.ApiException;
import com.smartai.core.platform.api.ApiMeta;
import com.smartai.core.platform.api.ApiRequestContext;
import com.smartai.core.platform.api.PageMeta;
import com.smartai.core.recruitment.agent.KnowledgeModels.DocumentCreateRequest;
import com.smartai.core.recruitment.agent.KnowledgeModels.DocumentPatchRequest;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeCitation;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeDocument;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeUploadSession;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeVersion;
import com.smartai.core.recruitment.agent.KnowledgeModels.ReasonRequest;
import com.smartai.core.recruitment.agent.KnowledgeModels.UploadCompleteRequest;
import com.smartai.core.recruitment.agent.KnowledgeModels.UploadSessionCreateRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanCheckpoint;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanReviewRequest;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
final class KnowledgeController {

	private static final String MERGE_PATCH = "application/merge-patch+json";

	private final KnowledgeService service;
	private final TenantActorResolver tenantActorResolver;

	KnowledgeController(KnowledgeService service, TenantActorResolver tenantActorResolver) {
		this.service = service;
		this.tenantActorResolver = tenantActorResolver;
	}

	@PostMapping("/api/core/v1/knowledge-documents")
	ResponseEntity<ApiEnvelope<KnowledgeDocument, ApiMeta>> createDocument(
			@Valid @RequestBody DocumentCreateRequest body,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.createDocument(
			actor, idempotencyKey, body,
			new KnowledgeService.AuditContext(context.requestId(), context.traceId()));
		KnowledgeDocument document = result.value();
		return ResponseEntity.status(HttpStatus.CREATED)
			.location(URI.create("/api/core/v1/knowledge-documents/" + document.id()))
			.eTag(Long.toString(document.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), document));
	}

	@GetMapping("/api/core/v1/knowledge-documents")
	ResponseEntity<ApiEnvelope<List<KnowledgeDocument>, PageMeta>> listDocuments(
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "50") int limit,
			HttpServletRequest request) {
		var page = service.listDocuments(
			tenantActorResolver.resolve(request), type, status, cursor, limit);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok(ApiEnvelope.page(
			context.requestId(), context.traceId(), page.items(), limit, page.hasMore(), page.nextCursor()));
	}

	@GetMapping("/api/core/v1/knowledge-documents/{documentId}")
	ResponseEntity<ApiEnvelope<KnowledgeDocument, ApiMeta>> getDocument(
			@PathVariable UUID documentId,
			HttpServletRequest request) {
		KnowledgeDocument document = service.getDocument(
			tenantActorResolver.resolve(request), documentId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(document.version()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), document));
	}

	@PatchMapping(value = "/api/core/v1/knowledge-documents/{documentId}", consumes = MERGE_PATCH)
	ResponseEntity<ApiEnvelope<KnowledgeDocument, ApiMeta>> patchDocument(
			@PathVariable UUID documentId,
			@Valid @RequestBody DocumentPatchRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.patchDocument(
			actor, documentId, VersionPrecondition.require(ifMatch), idempotencyKey, body,
			new KnowledgeService.AuditContext(context.requestId(), context.traceId()));
		KnowledgeDocument document = result.value();
		return ResponseEntity.ok()
			.eTag(Long.toString(document.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), document));
	}

	@PostMapping(
		value = "/api/core/v1/knowledge-documents/{documentId}/versions",
		consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ApiEnvelope<KnowledgeVersion, ApiMeta>> createVersion(
			@PathVariable UUID documentId,
			@RequestPart("file") MultipartFile file,
			@RequestParam String sha256,
			@RequestParam(required = false) OffsetDateTime effectiveFrom,
			@RequestParam(required = false) OffsetDateTime effectiveTo,
			@RequestParam(required = false) String changeSummary,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.createVersion(
			actor, documentId, idempotencyKey,
			file.getOriginalFilename(), file.getContentType(), bytes(file), sha256,
			effectiveFrom, effectiveTo, changeSummary,
			new KnowledgeService.AuditContext(context.requestId(), context.traceId()));
		KnowledgeVersion version = result.value();
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.location(URI.create("/api/core/v1/knowledge-versions/" + version.id()))
			.eTag(Long.toString(version.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), version));
	}

	@GetMapping("/api/core/v1/knowledge-documents/{documentId}/versions")
	ResponseEntity<ApiEnvelope<List<KnowledgeVersion>, PageMeta>> listVersions(
			@PathVariable UUID documentId,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "50") int limit,
			HttpServletRequest request) {
		var page = service.listVersions(
			tenantActorResolver.resolve(request), documentId, cursor, limit);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok(ApiEnvelope.page(
			context.requestId(), context.traceId(), page.items(), limit, page.hasMore(), page.nextCursor()));
	}

	@GetMapping("/api/core/v1/knowledge-versions/{knowledgeVersionId}")
	ResponseEntity<ApiEnvelope<KnowledgeVersion, ApiMeta>> getVersion(
			@PathVariable UUID knowledgeVersionId,
			HttpServletRequest request) {
		KnowledgeVersion version = service.getVersion(
			tenantActorResolver.resolve(request), knowledgeVersionId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(version.version()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), version));
	}

	@PostMapping("/api/core/v1/knowledge-documents/{documentId}/upload-sessions")
	ResponseEntity<ApiEnvelope<KnowledgeUploadSession, ApiMeta>> createUploadSession(
			@PathVariable UUID documentId,
			@Valid @RequestBody UploadSessionCreateRequest body,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.createUploadSession(
			actor, documentId, idempotencyKey, body,
			new KnowledgeService.AuditContext(context.requestId(), context.traceId()));
		KnowledgeUploadSession session = result.value();
		return ResponseEntity.status(HttpStatus.CREATED)
			.eTag(Long.toString(session.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), session));
	}

	@PutMapping(
		value = "/api/core/v1/knowledge-upload-sessions/{uploadSessionId}/content",
		consumes = MediaType.ALL_VALUE)
	ResponseEntity<ApiEnvelope<KnowledgeUploadSession, ApiMeta>> storeUploadContent(
			@PathVariable UUID uploadSessionId,
			@RequestBody byte[] body,
			@RequestHeader("Content-Type") String contentType,
			@RequestHeader("X-Content-SHA256") String sha256,
			HttpServletRequest request) {
		KnowledgeUploadSession session = service.storeUpload(
			tenantActorResolver.resolve(request), uploadSessionId, body, contentType, sha256);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(session.version()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), session));
	}

	@PostMapping("/api/core/v1/knowledge-upload-sessions/{uploadSessionId}/complete")
	ResponseEntity<ApiEnvelope<KnowledgeVersion, ApiMeta>> completeUpload(
			@PathVariable UUID uploadSessionId,
			@Valid @RequestBody UploadCompleteRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.completeUpload(
			actor, uploadSessionId, VersionPrecondition.require(ifMatch), idempotencyKey, body,
			new KnowledgeService.AuditContext(context.requestId(), context.traceId()));
		KnowledgeVersion version = result.value();
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.eTag(Long.toString(version.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), version));
	}

	@PostMapping("/api/core/v1/knowledge-versions/{knowledgeVersionId}/review-requests")
	ResponseEntity<ApiEnvelope<HumanCheckpoint, ApiMeta>> requestReview(
			@PathVariable UUID knowledgeVersionId,
			@Valid @RequestBody HumanReviewRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.requestReview(
			actor, knowledgeVersionId, VersionPrecondition.require(ifMatch), idempotencyKey, body,
			new KnowledgeService.AuditContext(context.requestId(), context.traceId()));
		HumanCheckpoint checkpoint = result.value();
		return ResponseEntity.status(HttpStatus.CREATED)
			.eTag(Long.toString(checkpoint.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), checkpoint));
	}

	@PostMapping("/api/core/v1/knowledge-versions/{knowledgeVersionId}/deactivate")
	ResponseEntity<ApiEnvelope<KnowledgeVersion, ApiMeta>> deactivateVersion(
			@PathVariable UUID knowledgeVersionId,
			@Valid @RequestBody ReasonRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.deactivateVersion(
			actor, knowledgeVersionId, VersionPrecondition.require(ifMatch), idempotencyKey, body,
			new KnowledgeService.AuditContext(context.requestId(), context.traceId()));
		KnowledgeVersion version = result.value();
		return ResponseEntity.ok()
			.eTag(Long.toString(version.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), version));
	}

	@GetMapping("/api/core/v1/knowledge-evidence/{evidenceId}")
	ResponseEntity<ApiEnvelope<KnowledgeCitation, ApiMeta>> resolveEvidence(
			@PathVariable UUID evidenceId,
			HttpServletRequest request) {
		KnowledgeCitation citation = service.resolveEvidence(
			tenantActorResolver.resolve(request), evidenceId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok(ApiEnvelope.success(context.requestId(), context.traceId(), citation));
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException exception) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY, "KNOWLEDGE_UPLOAD_FAILED", "Unable to read the uploaded file");
		}
	}
}

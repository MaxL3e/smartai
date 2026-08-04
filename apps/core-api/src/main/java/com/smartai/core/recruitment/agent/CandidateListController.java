package com.smartai.core.recruitment.agent;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartai.core.platform.api.ApiEnvelope;
import com.smartai.core.platform.api.ApiException;
import com.smartai.core.platform.api.ApiMeta;
import com.smartai.core.platform.api.ApiRequestContext;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListConfirmRequest;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListPreview;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListPreviewRequest;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListReviewRequest;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListVersion;
import com.smartai.core.recruitment.agent.CandidateListModels.RecommendationReport;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanCheckpoint;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
final class CandidateListController {

	private final CandidateListService service;
	private final TenantActorResolver tenantActorResolver;
	private final ObjectMapper objectMapper;

	CandidateListController(
			CandidateListService service,
			TenantActorResolver tenantActorResolver,
			ObjectMapper objectMapper) {
		this.service = service;
		this.tenantActorResolver = tenantActorResolver;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/api/core/v1/recruitment-tasks/{taskId}/candidate-list-previews")
	ResponseEntity<ApiEnvelope<CandidateListPreview, ApiMeta>> createPreview(
			@PathVariable UUID taskId,
			@Valid @RequestBody CandidateListPreviewRequest body,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.createPreview(
			actor, taskId, idempotencyKey, body,
			new CandidateListService.AuditContext(context.requestId(), context.traceId()));
		return ResponseEntity.ok()
			.eTag(Long.toString(result.value().version()))
			.header("X-SmartAI-Input-Hash", result.value().inputHash())
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), result.value()));
	}

	@GetMapping("/api/core/v1/candidate-list-previews/{previewId}")
	ResponseEntity<ApiEnvelope<CandidateListPreview, ApiMeta>> preview(
			@PathVariable UUID previewId,
			HttpServletRequest request) {
		CandidateListPreview value = service.getPreview(tenantActorResolver.resolve(request), previewId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(value.version()))
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.header("X-SmartAI-Input-Hash", value.inputHash())
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), value));
	}

	@PostMapping("/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests")
	ResponseEntity<ApiEnvelope<HumanCheckpoint, ApiMeta>> requestReview(
			@PathVariable UUID taskId,
			@Valid @RequestBody CandidateListReviewRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.requestReview(
			actor, taskId, VersionPrecondition.require(ifMatch), idempotencyKey, body,
			new CandidateListService.AuditContext(context.requestId(), context.traceId()));
		return ResponseEntity.status(HttpStatus.CREATED)
			.eTag(Long.toString(result.value().version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), result.value()));
	}

	@PostMapping("/api/core/v1/recruitment-tasks/{taskId}/candidate-lists/confirm")
	ResponseEntity<ApiEnvelope<CandidateListVersion, ApiMeta>> confirm(
			@PathVariable UUID taskId,
			@Valid @RequestBody CandidateListConfirmRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.confirm(
			actor, taskId, VersionPrecondition.require(ifMatch), idempotencyKey, body,
			new CandidateListService.AuditContext(context.requestId(), context.traceId()));
		return ResponseEntity.status(HttpStatus.CREATED)
			.eTag(result.value().contentHash())
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), result.value()));
	}

	@GetMapping("/api/core/v1/recruitment-tasks/{taskId}/candidate-lists/current")
	ResponseEntity<ApiEnvelope<CandidateListVersion, ApiMeta>> currentCandidateList(
			@PathVariable UUID taskId,
			HttpServletRequest request) {
		CandidateListVersion value = service.getCurrentCandidateList(tenantActorResolver.resolve(request), taskId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(value.contentHash())
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), value));
	}

	@GetMapping("/api/core/v1/recruitment-tasks/{taskId}/recommendation-reports/current")
	ResponseEntity<ApiEnvelope<RecommendationReport, ApiMeta>> currentReport(
			@PathVariable UUID taskId,
			HttpServletRequest request) {
		RecommendationReport value = service.getCurrentReport(tenantActorResolver.resolve(request), taskId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return reportEnvelope(value, context);
	}

	@GetMapping("/api/core/v1/recommendation-reports/{reportId}")
	ResponseEntity<ApiEnvelope<RecommendationReport, ApiMeta>> report(
			@PathVariable UUID reportId,
			HttpServletRequest request) {
		RecommendationReport value = service.getReport(tenantActorResolver.resolve(request), reportId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return reportEnvelope(value, context);
	}

	@GetMapping("/api/core/v1/recommendation-reports/{reportId}/download")
	ResponseEntity<byte[]> downloadReport(
			@PathVariable UUID reportId,
			@RequestParam(defaultValue = "TXT") String format,
			HttpServletRequest request) {
		RecommendationReport report = service.getReport(tenantActorResolver.resolve(request), reportId);
		String normalized = format.strip().toUpperCase(Locale.ROOT);
		byte[] body;
		MediaType contentType;
		String extension;
		if ("TXT".equals(normalized)) {
			body = service.textReport(report);
			contentType = new MediaType("text", "plain", StandardCharsets.UTF_8);
			extension = "txt";
		}
		else if ("JSON".equals(normalized)) {
			body = json(report);
			contentType = MediaType.APPLICATION_JSON;
			extension = "json";
		}
		else {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "format must be TXT or JSON");
		}
		String fileName = "recommendation-report-" + report.id() + "." + extension;
		return ResponseEntity.ok()
			.contentType(contentType)
			.eTag(report.contentHash())
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
			.body(body);
	}

	private ResponseEntity<ApiEnvelope<RecommendationReport, ApiMeta>> reportEnvelope(
			RecommendationReport value,
			ApiRequestContext context) {
		return ResponseEntity.ok()
			.eTag(value.contentHash())
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), value));
	}

	private byte[] json(RecommendationReport report) {
		try {
			return objectMapper.writeValueAsBytes(report);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize recommendation report", exception);
		}
	}
}

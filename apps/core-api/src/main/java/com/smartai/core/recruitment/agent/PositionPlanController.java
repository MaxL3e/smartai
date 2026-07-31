package com.smartai.core.recruitment.agent;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.smartai.core.platform.api.ApiEnvelope;
import com.smartai.core.platform.api.ApiMeta;
import com.smartai.core.platform.api.ApiRequestContext;
import com.smartai.core.recruitment.agent.PositionPlanModels.AgentRun;
import com.smartai.core.recruitment.agent.PositionPlanModels.GenerateRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanCheckpoint;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanReviewRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.PatchRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.PositionPlanVersion;
import com.smartai.core.recruitment.agent.PositionPlanService.AuditContext;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
final class PositionPlanController {

	private final PositionPlanService service;
	private final TenantActorResolver tenantActorResolver;

	PositionPlanController(PositionPlanService service, TenantActorResolver tenantActorResolver) {
		this.service = service;
		this.tenantActorResolver = tenantActorResolver;
	}

	@GetMapping("/api/core/v1/recruitment-tasks/{taskId}/position-plan")
	ResponseEntity<ApiEnvelope<PositionPlanVersion, ApiMeta>> getCurrent(
			@PathVariable UUID taskId,
			HttpServletRequest request) {
		PositionPlanVersion plan = service.getCurrent(tenantActorResolver.resolve(request), taskId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(plan.version()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), plan));
	}

	@PostMapping("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations")
	ResponseEntity<ApiEnvelope<AgentRun, ApiMeta>> generate(
			@PathVariable UUID taskId,
			@Valid @RequestBody GenerateRequest body,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.generate(
			actor, taskId, idempotencyKey, body, new AuditContext(context.requestId(), context.traceId()));
		AgentRun run = result.value();
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.location(URI.create("/api/core/v1/agent-runs/" + run.id()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), run));
	}

	@GetMapping("/api/core/v1/position-plan-versions/{planVersionId}")
	ResponseEntity<ApiEnvelope<PositionPlanVersion, ApiMeta>> getVersion(
			@PathVariable UUID planVersionId,
			HttpServletRequest request) {
		PositionPlanVersion plan = service.getVersion(tenantActorResolver.resolve(request), planVersionId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(plan.version()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), plan));
	}

	@PatchMapping(
		path = "/api/core/v1/position-plan-versions/{planVersionId}",
		consumes = "application/merge-patch+json")
	ResponseEntity<ApiEnvelope<PositionPlanVersion, ApiMeta>> patch(
			@PathVariable UUID planVersionId,
			@Valid @RequestBody PatchRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.patch(
			actor, planVersionId, VersionPrecondition.require(ifMatch), idempotencyKey, body,
			new AuditContext(context.requestId(), context.traceId()));
		PositionPlanVersion plan = result.value();
		return ResponseEntity.ok()
			.eTag(Long.toString(plan.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), plan));
	}

	@PostMapping("/api/core/v1/position-plan-versions/{planVersionId}/review-requests")
	ResponseEntity<ApiEnvelope<HumanCheckpoint, ApiMeta>> requestReview(
			@PathVariable UUID planVersionId,
			@Valid @RequestBody HumanReviewRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.requestReview(
			actor, planVersionId, VersionPrecondition.require(ifMatch), idempotencyKey, body,
			new AuditContext(context.requestId(), context.traceId()));
		HumanCheckpoint checkpoint = result.value();
		return ResponseEntity.status(HttpStatus.CREATED)
			.location(URI.create("/api/core/v1/human-checkpoints/" + checkpoint.id()))
			.eTag(Long.toString(checkpoint.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), checkpoint));
	}
}

package com.smartai.core.recruitment.agent;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartai.core.platform.api.ApiEnvelope;
import com.smartai.core.platform.api.ApiException;
import com.smartai.core.platform.api.ApiMeta;
import com.smartai.core.platform.api.ApiRequestContext;
import com.smartai.core.platform.api.PageMeta;
import com.smartai.core.recruitment.agent.PositionPlanModels.DecisionRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanCheckpoint;
import com.smartai.core.recruitment.agent.PositionPlanService.AuditContext;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/core/v1/human-checkpoints")
final class HumanCheckpointController {

	private final PositionPlanService service;
	private final KnowledgeService knowledgeService;
	private final CandidateListService candidateListService;
	private final TenantActorResolver tenantActorResolver;

	HumanCheckpointController(
			PositionPlanService service,
			KnowledgeService knowledgeService,
			CandidateListService candidateListService,
			TenantActorResolver tenantActorResolver) {
		this.service = service;
		this.knowledgeService = knowledgeService;
		this.candidateListService = candidateListService;
		this.tenantActorResolver = tenantActorResolver;
	}

	@GetMapping
	ResponseEntity<ApiEnvelope<List<HumanCheckpoint>, PageMeta>> list(
			@RequestParam(required = false) UUID taskId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "50") int limit,
			HttpServletRequest request) {
		var page = service.listCheckpoints(
			tenantActorResolver.resolve(request), taskId, status, type, cursor, limit);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok(ApiEnvelope.page(
			context.requestId(), context.traceId(), page.items(), limit, page.hasMore(), page.nextCursor()));
	}

	@GetMapping("/{checkpointId}")
	ResponseEntity<ApiEnvelope<HumanCheckpoint, ApiMeta>> get(
			@PathVariable UUID checkpointId,
			HttpServletRequest request) {
		HumanCheckpoint checkpoint = service.getCheckpoint(tenantActorResolver.resolve(request), checkpointId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(checkpoint.version()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), checkpoint));
	}

	@PostMapping("/{checkpointId}/decisions")
	ResponseEntity<ApiEnvelope<HumanCheckpoint, ApiMeta>> decide(
			@PathVariable UUID checkpointId,
			@Valid @RequestBody DecisionRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		long expectedVersion = VersionPrecondition.require(ifMatch);
		HumanCheckpoint current = service.getCheckpoint(actor, checkpointId);
		HumanCheckpoint checkpoint;
		boolean replayed;
		if ("CONFIRM_CANDIDATE_LIST".equals(current.type())) {
			var result = candidateListService.decideReview(
				actor, checkpointId, expectedVersion, idempotencyKey, body,
				new CandidateListService.AuditContext(context.requestId(), context.traceId()));
			checkpoint = result.value();
			replayed = result.replayed();
			if ("EXPIRED".equals(checkpoint.status())) {
				throw new ApiException(HttpStatus.GONE, "CHECKPOINT_EXPIRED", "Checkpoint has expired");
			}
		}
		else if ("PUBLISH_KNOWLEDGE".equals(current.type())) {
			var result = knowledgeService.decideReview(
				actor, checkpointId, expectedVersion, idempotencyKey, body,
				new KnowledgeService.AuditContext(context.requestId(), context.traceId()));
			checkpoint = result.value();
			replayed = result.replayed();
		}
		else {
			var result = service.decide(
				actor, checkpointId, expectedVersion, idempotencyKey, body,
				new AuditContext(context.requestId(), context.traceId()));
			checkpoint = result.value();
			replayed = result.replayed();
		}
		return ResponseEntity.ok()
			.eTag(Long.toString(checkpoint.version()))
			.header("Idempotency-Replayed", Boolean.toString(replayed))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), checkpoint));
	}
}

package com.smartai.core.recruitment.agent;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.smartai.core.platform.api.ApiEnvelope;
import com.smartai.core.platform.api.ApiMeta;
import com.smartai.core.platform.api.ApiRequestContext;
import com.smartai.core.recruitment.agent.RequirementDraftModels.CreateRequest;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ConvertRequest;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Draft;
import com.smartai.core.recruitment.agent.RequirementDraftModels.PatchRequest;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Task;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;
import com.smartai.core.recruitment.agent.RequirementDraftService.CommandResult;
import com.smartai.core.recruitment.agent.RequirementDraftService.CreateResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/core/v1/requirement-drafts")
final class RequirementDraftController {

	private final RequirementDraftService service;
	private final TenantActorResolver tenantActorResolver;

	RequirementDraftController(RequirementDraftService service, TenantActorResolver tenantActorResolver) {
		this.service = service;
		this.tenantActorResolver = tenantActorResolver;
	}

	@PostMapping
	ResponseEntity<ApiEnvelope<Draft, ApiMeta>> create(
			@Valid @RequestBody CreateRequest body,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		CreateResult result = service.create(actor, idempotencyKey, body);
		Draft draft = result.draft();
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.location(URI.create("/api/core/v1/requirement-drafts/" + draft.id()))
			.eTag(Long.toString(draft.version()))
			.header("X-SmartAI-Input-Hash", service.confirmationHash(draft))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), draft));
	}

	@GetMapping("/{draftId}")
	ResponseEntity<ApiEnvelope<Draft, ApiMeta>> get(
			@PathVariable UUID draftId,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		Draft draft = service.get(actor, draftId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(draft.version()))
			.header("X-SmartAI-Input-Hash", service.confirmationHash(draft))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), draft));
	}

	@PatchMapping(path = "/{draftId}", consumes = "application/merge-patch+json")
	ResponseEntity<ApiEnvelope<Draft, ApiMeta>> patch(
			@PathVariable UUID draftId,
			@Valid @RequestBody PatchRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		CommandResult<Draft> result = service.patch(
			actor, draftId, VersionPrecondition.require(ifMatch), idempotencyKey, body);
		Draft draft = result.value();
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(draft.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.header("X-SmartAI-Input-Hash", service.confirmationHash(draft))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), draft));
	}

	@PostMapping("/{draftId}/convert")
	ResponseEntity<ApiEnvelope<Task, ApiMeta>> convert(
			@PathVariable UUID draftId,
			@Valid @RequestBody ConvertRequest body,
			@RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		CommandResult<Task> result = service.convert(
			actor, draftId, VersionPrecondition.require(ifMatch), idempotencyKey, body);
		Task task = result.value();
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.location(URI.create("/api/core/v1/recruitment-tasks/" + task.id()))
			.eTag(Long.toString(task.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), task));
	}
}

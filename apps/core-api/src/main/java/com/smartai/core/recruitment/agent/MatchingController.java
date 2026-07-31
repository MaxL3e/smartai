package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartai.core.platform.api.ApiEnvelope;
import com.smartai.core.platform.api.ApiException;
import com.smartai.core.platform.api.ApiMeta;
import com.smartai.core.platform.api.ApiRequestContext;
import com.smartai.core.platform.api.PageMeta;
import com.smartai.core.recruitment.agent.CandidateNormalizationService.AuditContext;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateInputReceipt;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateInputRequest;
import com.smartai.core.recruitment.agent.MatchingModels.MatchResult;
import com.smartai.core.recruitment.agent.MatchingModels.MatchRun;
import com.smartai.core.recruitment.agent.MatchingModels.MatchRunCreateRequest;
import com.smartai.core.recruitment.agent.MatchingModels.TaskCandidate;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
final class MatchingController {

	private final CandidateNormalizationService normalizationService;
	private final MatchingService matchingService;
	private final TenantActorResolver tenantActorResolver;

	MatchingController(
			CandidateNormalizationService normalizationService,
			MatchingService matchingService,
			TenantActorResolver tenantActorResolver) {
		this.normalizationService = normalizationService;
		this.matchingService = matchingService;
		this.tenantActorResolver = tenantActorResolver;
	}

	@PostMapping("/api/core/v1/candidate-inputs")
	ResponseEntity<ApiEnvelope<CandidateInputReceipt, ApiMeta>> normalizeCandidate(
			@Valid @RequestBody CandidateInputRequest body,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = normalizationService.normalize(
			actor, idempotencyKey, body, new AuditContext(context.requestId(), context.traceId()));
		CandidateInputReceipt receipt = result.value();
		return ResponseEntity.status(HttpStatus.CREATED)
			.location(URI.create("/api/core/v1/candidates/" + receipt.candidate().id()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), receipt));
	}

	@PostMapping("/api/core/v1/recruitment-tasks/{taskId}/match-runs")
	ResponseEntity<ApiEnvelope<MatchRun, ApiMeta>> createRun(
			@PathVariable UUID taskId,
			@Valid @RequestBody MatchRunCreateRequest body,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = matchingService.createRun(
			actor, taskId, idempotencyKey, body,
			new MatchingService.AuditContext(context.requestId(), context.traceId()));
		MatchRun run = result.value();
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.location(URI.create("/api/core/v1/match-runs/" + run.id()))
			.eTag(Long.toString(run.version()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), run));
	}

	@GetMapping("/api/core/v1/match-runs/{matchRunId}")
	ResponseEntity<ApiEnvelope<MatchRun, ApiMeta>> getRun(
			@PathVariable UUID matchRunId,
			HttpServletRequest request) {
		MatchRun run = matchingService.getRun(tenantActorResolver.resolve(request), matchRunId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(run.version()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), run));
	}

	@GetMapping("/api/core/v1/match-runs/{matchRunId}/results")
	ResponseEntity<ApiEnvelope<List<MatchResult>, PageMeta>> listResults(
			@PathVariable UUID matchRunId,
			@RequestParam(required = false) BigDecimal minimumScore,
			@RequestParam(required = false) String reviewStatus,
			@RequestParam(defaultValue = "50") int limit,
			HttpServletRequest request) {
		validateLimit(limit);
		List<MatchResult> filtered = matchingService.listResults(
			tenantActorResolver.resolve(request), matchRunId).stream()
			.filter(result -> minimumScore == null || result.totalScore().compareTo(minimumScore) >= 0)
			.filter(result -> reviewStatus == null || reviewStatus.equals(result.reviewStatus()))
			.toList();
		boolean hasMore = filtered.size() > limit;
		List<MatchResult> items = filtered.stream().limit(limit).toList();
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok(ApiEnvelope.page(
			context.requestId(), context.traceId(), items, limit, hasMore, null));
	}

	@GetMapping("/api/core/v1/match-results/{matchResultId}")
	ResponseEntity<ApiEnvelope<MatchResult, ApiMeta>> getResult(
			@PathVariable UUID matchResultId,
			HttpServletRequest request) {
		MatchResult result = matchingService.getResult(
			tenantActorResolver.resolve(request), matchResultId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(result.version()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), result));
	}

	@GetMapping("/api/core/v1/recruitment-tasks/{taskId}/task-candidates")
	ResponseEntity<ApiEnvelope<List<TaskCandidate>, PageMeta>> listTaskCandidates(
			@PathVariable UUID taskId,
			@RequestParam(required = false) String selectionStatus,
			@RequestParam(defaultValue = "50") int limit,
			HttpServletRequest request) {
		validateLimit(limit);
		List<TaskCandidate> filtered = matchingService.listTaskCandidates(
			tenantActorResolver.resolve(request), taskId).stream()
			.filter(candidate -> selectionStatus == null || selectionStatus.equals(candidate.selectionStatus()))
			.toList();
		boolean hasMore = filtered.size() > limit;
		List<TaskCandidate> items = filtered.stream().limit(limit).toList();
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok(ApiEnvelope.page(
			context.requestId(), context.traceId(), items, limit, hasMore, null));
	}

	private static void validateLimit(int limit) {
		if (limit < 1 || limit > 200) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "limit must be between 1 and 200");
		}
	}
}

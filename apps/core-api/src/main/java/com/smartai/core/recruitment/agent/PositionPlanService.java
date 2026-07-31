package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartai.core.platform.api.ApiException;
import com.smartai.core.recruitment.agent.PositionPlanModels.AgentRun;
import com.smartai.core.recruitment.agent.PositionPlanModels.DecisionRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.GenerateRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanCheckpoint;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanReviewRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.PatchRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.PositionPlanVersion;
import com.smartai.core.recruitment.agent.PositionPlanModels.RecommendationThreshold;
import com.smartai.core.recruitment.agent.PositionPlanModels.ScorecardVersion;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Draft;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Task;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

@Service
public class PositionPlanService {

	private static final String GENERATE = "GENERATE_POSITION_PLAN";
	private static final String PATCH = "PATCH_POSITION_PLAN";
	private static final String REVIEW = "REQUEST_POSITION_PLAN_REVIEW";
	private static final String DECIDE = "DECIDE_POSITION_PLAN_CHECKPOINT";

	private final PositionPlanRepository repository;
	private final RequirementDraftRepository draftRepository;
	private final DeterministicPositionPlanGenerator generator;
	private final PositionPlanHasher hasher;
	private final Clock clock = Clock.systemUTC();

	public PositionPlanService(
			PositionPlanRepository repository,
			RequirementDraftRepository draftRepository,
			DeterministicPositionPlanGenerator generator,
			PositionPlanHasher hasher) {
		this.repository = repository;
		this.draftRepository = draftRepository;
		this.generator = generator;
		this.hasher = hasher;
	}

	@Transactional
	CommandResult<AgentRun> generate(
			TenantActor actor,
			UUID taskId,
			UUID idempotencyKey,
			GenerateRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(GENERATE, taskId, null, request);
		var previousCommand = repository.findCommand(actor.tenantId(), GENERATE, idempotencyKey, AgentRun.class);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}

		Task currentTask = requireTask(actor, taskId);
		ensureRolePlanStage(currentTask);
		if (request.knowledgeVersionRefs().stream().distinct().count() != request.knowledgeVersionRefs().size()) {
			throw validation("knowledgeVersionRefs must be unique");
		}
		if (!request.knowledgeVersionRefs().isEmpty()) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"KNOWLEDGE_VERSION_NOT_AVAILABLE",
				"The deterministic demo provider currently supports an explicit empty knowledge snapshot only");
		}

		UUID sourceDraftId = repository.findSourceDraftId(actor.tenantId(), taskId).orElseThrow(this::notFound);
		Draft sourceDraft = draftRepository.find(actor.tenantId(), sourceDraftId).orElseThrow(this::notFound);
		verifySourceDraftRef(request.requirementDraftRef(), sourceDraft);

		OffsetDateTime now = now();
		UUID runId = UUID.randomUUID();
		AgentRun running = new AgentRun(
			runId,
			taskId,
			"POSITION_PLAN_GENERATION",
			"RUNNING",
			DeterministicPositionPlanGenerator.WORKFLOW_VERSION,
			audit.traceId(),
			null,
			now,
			now,
			null);
		repository.insertAgentRun(
			actor.tenantId(), running, DeterministicPositionPlanGenerator.GENERATOR_KIND,
			actor.userId().toString());

		int versionNo = repository.nextPlanVersionNo(actor.tenantId(), taskId);
		PositionPlanVersion plan = generator.generate(
			currentTask, sourceDraft, runId, versionNo, request.instructions(), now);
		repository.insertPlan(
			actor.tenantId(), plan, DeterministicPositionPlanGenerator.GENERATOR_KIND,
			actor.userId().toString());

		Task updatedTask = updateTaskView(
			currentTask,
			currentTask.businessStage(),
			"WAITING_HUMAN",
			new ResourceRef("PositionPlanVersion", plan.id(), plan.version()),
			now);
		if (repository.updateTask(actor.tenantId(), currentTask, updatedTask, actor.userId().toString()) != 1) {
			throw versionConflict();
		}

		repository.updateAgentRunStatus(
			actor.tenantId(), runId, "WAITING_HUMAN", plan.id(), null, now, actor.userId().toString());
		AgentRun result = new AgentRun(
			running.id(), running.taskId(), running.runType(), "WAITING_HUMAN", running.workflowVersion(),
			running.traceId(), null, running.createdAt(), running.startedAt(), null);
		repository.insertCommand(
			actor.tenantId(), taskId, taskId, GENERATE, idempotencyKey, requestHash, result, 1L,
			actor.userId().toString());
		repository.appendAudit(
			actor.tenantId(), user(actor), "PositionPlanVersion", plan.id(), "POSITION_PLAN_GENERATED",
			audit.requestId(), audit.traceId(),
			Map.of(
				"taskId", taskId,
				"planVersion", plan.version(),
				"generatorKind", DeterministicPositionPlanGenerator.GENERATOR_KIND,
				"knowledgeReferenceCount", 0,
				"agentRunId", runId),
			now);
		return new CommandResult<>(result, false);
	}

	@Transactional(readOnly = true)
	PositionPlanVersion getCurrent(TenantActor actor, UUID taskId) {
		requireTask(actor, taskId);
		return repository.findCurrentPlan(actor.tenantId(), taskId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND,
				"RESOURCE_NOT_FOUND",
				"Current position plan was not found"));
	}

	@Transactional(readOnly = true)
	PositionPlanVersion getVersion(TenantActor actor, UUID planId) {
		return requirePlan(actor, planId);
	}

	@Transactional
	CommandResult<PositionPlanVersion> patch(
			TenantActor actor,
			UUID planId,
			long expectedVersion,
			UUID idempotencyKey,
			PatchRequest request,
			AuditContext audit) {
		ensurePatchNotEmpty(request);
		String requestHash = hasher.commandHash(PATCH, planId, expectedVersion, request);
		var previousCommand = repository.findCommand(actor.tenantId(), PATCH, idempotencyKey, PositionPlanVersion.class);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}

		PositionPlanVersion current = requirePlan(actor, planId);
		ensurePlanStatus(current, "DRAFT");
		VersionPrecondition.verify(expectedVersion, current.version());
		Task currentTask = requireTask(actor, current.taskId());
		ensureRolePlanStage(currentTask);

		ScorecardVersion scorecard = request.scorecard() == null
			? current.scorecard()
			: normalizedScorecard(request.scorecard(), current);
		validateScorecard(scorecard);
		OffsetDateTime now = now();
		PositionPlanVersion updated = new PositionPlanVersion(
			current.id(), current.taskId(), current.versionNo(), current.status(), current.version() + 1,
			request.jobDescription() == null ? current.jobDescription() : request.jobDescription().strip(),
			request.responsibilities() == null ? current.responsibilities() : List.copyOf(request.responsibilities()),
			request.requirements() == null ? current.requirements() : List.copyOf(request.requirements()),
			request.hardConstraints() == null ? current.hardConstraints() : List.copyOf(request.hardConstraints()),
			scorecard, current.generatedBy(), current.basedOnRunId(), current.knowledgeVersionRefs(),
			current.promptVersion(), "0".repeat(64),
			request.changeSummary() == null ? current.changeSummary() : request.changeSummary().strip(),
			current.approvalCheckpointRef(), current.approvedBy(), current.approvedAt(), current.createdAt(), now);
		updated = DeterministicPositionPlanGenerator.withContentHash(updated, hasher.planContentHash(updated));
		if (repository.updatePlan(actor.tenantId(), current, updated, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		Task updatedTask = updateTaskView(
			currentTask, currentTask.businessStage(), currentTask.executionStatus(),
			new ResourceRef("PositionPlanVersion", updated.id(), updated.version()), now);
		if (repository.updateTask(actor.tenantId(), currentTask, updatedTask, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		repository.insertCommand(
			actor.tenantId(), current.taskId(), planId, PATCH, idempotencyKey, requestHash, updated,
			updated.version(), actor.userId().toString());
		repository.appendAudit(
			actor.tenantId(), user(actor), "PositionPlanVersion", planId, "POSITION_PLAN_DRAFT_UPDATED",
			audit.requestId(), audit.traceId(),
			Map.of("taskId", current.taskId(), "beforeVersion", current.version(), "afterVersion", updated.version()),
			now);
		return new CommandResult<>(updated, false);
	}

	@Transactional
	CommandResult<HumanCheckpoint> requestReview(
			TenantActor actor,
			UUID planId,
			long expectedVersion,
			UUID idempotencyKey,
			HumanReviewRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(REVIEW, planId, expectedVersion, request);
		var previousCommand = repository.findCommand(actor.tenantId(), REVIEW, idempotencyKey, HumanCheckpoint.class);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}

		PositionPlanVersion current = requirePlan(actor, planId);
		ensurePlanStatus(current, "DRAFT");
		VersionPrecondition.verify(expectedVersion, current.version());
		if (!Set.of("RECRUITMENT_MANAGER", "HIRING_MANAGER").contains(request.requiredRole())) {
			throw validation("G2 review requires RECRUITMENT_MANAGER or HIRING_MANAGER");
		}
		if (!secureEquals(current.contentHash(), request.inputHash())) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"CONFIRMATION_INPUT_CHANGED",
				"Review input does not match the current position plan");
		}
		OffsetDateTime now = now();
		if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
			throw validation("expiresAt must be in the future");
		}

		Task currentTask = requireTask(actor, current.taskId());
		ensureRolePlanStage(currentTask);
		PositionPlanVersion inReview = withState(
			current, "IN_REVIEW", current.version() + 1, null, null, null, now);
		UUID checkpointId = UUID.randomUUID();
		HumanCheckpoint checkpoint = new HumanCheckpoint(
			checkpointId,
			current.taskId(),
			"APPROVE_POSITION_PLAN",
			new ResourceRef("PositionPlanVersion", current.id(), inReview.version()),
			"PENDING",
			request.requiredRole(),
			request.assigneeUserId(),
			current.contentHash(),
			1L,
			"请核对岗位描述、职责、任职要求、硬条件、评分卡和推荐阈值。当前知识引用为 0。",
			user(actor),
			now,
			request.expiresAt(),
			null,
			request.comment(),
			null,
			null);
		inReview = withState(
			inReview, inReview.status(), inReview.version(),
			new ResourceRef("HumanCheckpoint", checkpointId, checkpoint.version()), null, null, now);
		if (repository.updatePlan(actor.tenantId(), current, inReview, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		UUID sourceDraftId = repository.findSourceDraftId(actor.tenantId(), current.taskId()).orElseThrow(this::notFound);
		repository.insertCheckpoint(actor.tenantId(), checkpoint, sourceDraftId, actor.userId().toString());
		Task updatedTask = updateTaskView(
			currentTask, "ROLE_PLAN", "WAITING_HUMAN",
			new ResourceRef("PositionPlanVersion", inReview.id(), inReview.version()), now);
		if (repository.updateTask(actor.tenantId(), currentTask, updatedTask, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		repository.insertCommand(
			actor.tenantId(), current.taskId(), planId, REVIEW, idempotencyKey, requestHash, checkpoint,
			checkpoint.version(), actor.userId().toString());
		repository.appendAudit(
			actor.tenantId(), user(actor), "HumanCheckpoint", checkpoint.id(), "POSITION_PLAN_REVIEW_REQUESTED",
			audit.requestId(), audit.traceId(),
			Map.of(
				"taskId", current.taskId(),
				"planVersionId", current.id(),
				"frozenPlanVersion", inReview.version(),
				"generatorKind", DeterministicPositionPlanGenerator.GENERATOR_KIND,
				"knowledgeReferenceCount", 0),
			now);
		return new CommandResult<>(checkpoint, false);
	}

	@Transactional(readOnly = true)
	HumanCheckpoint getCheckpoint(TenantActor actor, UUID checkpointId) {
		return requireCheckpoint(actor, checkpointId);
	}

	@Transactional
	CommandResult<HumanCheckpoint> decide(
			TenantActor actor,
			UUID checkpointId,
			long expectedVersion,
			UUID idempotencyKey,
			DecisionRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(DECIDE, checkpointId, expectedVersion, request);
		var previousCommand = repository.findCommand(actor.tenantId(), DECIDE, idempotencyKey, HumanCheckpoint.class);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}

		HumanCheckpoint current = requireCheckpoint(actor, checkpointId);
		if (!"APPROVE_POSITION_PLAN".equals(current.type())) {
			throw new ApiException(HttpStatus.CONFLICT, "CHECKPOINT_TYPE_MISMATCH", "Checkpoint is not a G2 gate");
		}
		VersionPrecondition.verify(expectedVersion, current.version());
		if (!"PENDING".equals(current.status())) {
			throw new ApiException(HttpStatus.CONFLICT, "CHECKPOINT_ALREADY_DECIDED", "Checkpoint is already terminal");
		}
		OffsetDateTime now = now();
		if (current.expiresAt() != null && !current.expiresAt().isAfter(now)) {
			throw new ApiException(HttpStatus.GONE, "CHECKPOINT_EXPIRED", "Checkpoint has expired");
		}
		if (!secureEquals(current.inputHash(), request.inputHash())) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"CONFIRMATION_INPUT_CHANGED",
				"Decision input does not match the frozen checkpoint content");
		}

		PositionPlanVersion plan = requirePlan(actor, current.resourceRef().id());
		if (plan.version() != current.resourceRef().version()
				|| !"IN_REVIEW".equals(plan.status())
				|| !secureEquals(plan.contentHash(), current.inputHash())) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"CHECKPOINT_RESOURCE_CHANGED",
				"Frozen position plan no longer matches the checkpoint");
		}

		Task task = requireTask(actor, current.taskId());
		String terminalStatus = switch (request.decision()) {
			case "APPROVE" -> "APPROVED";
			case "REJECT" -> "REJECTED";
			case "CANCEL" -> "CANCELLED";
			default -> throw validation("Unsupported checkpoint decision");
		};
		HumanCheckpoint decided = new HumanCheckpoint(
			current.id(), current.taskId(), current.type(), current.resourceRef(), terminalStatus,
			current.requiredRole(), current.assigneeUserId(), current.inputHash(), current.version() + 1,
			current.summary(), current.requestedBy(), current.requestedAt(), current.expiresAt(), request.decision(),
			request.comment(), user(actor), now);

		boolean approved = "APPROVE".equals(request.decision());
		PositionPlanVersion decidedPlan = withState(
			plan,
			approved ? "APPROVED" : "DRAFT",
			plan.version() + 1,
			approved ? new ResourceRef("HumanCheckpoint", current.id(), decided.version()) : null,
			approved ? user(actor) : null,
			approved ? now : null,
			now);
		if (repository.updatePlan(actor.tenantId(), plan, decidedPlan, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		Task updatedTask = updateTaskView(
			task,
			approved ? "TALENT_SEARCH" : "ROLE_PLAN",
			"IDLE",
			new ResourceRef("PositionPlanVersion", decidedPlan.id(), decidedPlan.version()),
			now);
		if (repository.updateTask(actor.tenantId(), task, updatedTask, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		if (repository.updateCheckpoint(actor.tenantId(), current, decided, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		repository.updateAgentRunStatus(
			actor.tenantId(), plan.basedOnRunId(), "SUCCEEDED", plan.id(), now, now, actor.userId().toString());
		repository.insertCommand(
			actor.tenantId(), current.taskId(), checkpointId, DECIDE, idempotencyKey, requestHash, decided,
			decided.version(), actor.userId().toString());
		repository.appendAudit(
			actor.tenantId(), user(actor), "HumanCheckpoint", checkpointId, "POSITION_PLAN_REVIEW_DECIDED",
			audit.requestId(), audit.traceId(),
			Map.of(
				"taskId", current.taskId(),
				"planVersionId", plan.id(),
				"decision", request.decision(),
				"resultingBusinessStage", updatedTask.businessStage()),
			now);
		return new CommandResult<>(decided, false);
	}

	@Transactional(readOnly = true)
	AgentRun getAgentRun(TenantActor actor, UUID runId) {
		return repository.findAgentRun(actor.tenantId(), runId)
			.orElseThrow(this::notFound);
	}

	private Task requireTask(TenantActor actor, UUID taskId) {
		return repository.findTask(actor.tenantId(), taskId).orElseThrow(this::notFound);
	}

	private PositionPlanVersion requirePlan(TenantActor actor, UUID planId) {
		return repository.findPlan(actor.tenantId(), planId).orElseThrow(this::notFound);
	}

	private HumanCheckpoint requireCheckpoint(TenantActor actor, UUID checkpointId) {
		return repository.findCheckpoint(actor.tenantId(), checkpointId).orElseThrow(this::notFound);
	}

	private void verifySourceDraftRef(ResourceRef reference, Draft draft) {
		if (!"RequirementDraft".equals(reference.type())
				|| !draft.id().equals(reference.id())
				|| draft.version() != reference.version()) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"SOURCE_VERSION_CONFLICT",
				"requirementDraftRef must identify the task's exact confirmed source draft version");
		}
	}

	private static void ensureRolePlanStage(Task task) {
		if (!"ACTIVE".equals(task.lifecycleStatus()) || !"ROLE_PLAN".equals(task.businessStage())) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"TASK_STAGE_CONFLICT",
				"Position plans can only be changed while an active task is in ROLE_PLAN");
		}
	}

	private static void ensurePlanStatus(PositionPlanVersion plan, String expected) {
		if (!expected.equals(plan.status())) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"POSITION_PLAN_STATE_CONFLICT",
				"Position plan must be " + expected + " for this operation");
		}
	}

	private ScorecardVersion normalizedScorecard(ScorecardVersion requested, PositionPlanVersion current) {
		if (!requested.id().equals(current.scorecard().id()) || requested.versionNo() != current.scorecard().versionNo()) {
			throw validation("A draft edit cannot replace the scorecard identity or business version");
		}
		ScorecardVersion normalized = new ScorecardVersion(
			requested.id(), requested.versionNo(), requested.totalScore(), List.copyOf(requested.criteria()),
			List.copyOf(requested.thresholds()), requested.missingEvidencePolicy(),
			requested.sensitiveFeaturePolicy(), "0".repeat(64));
		return new ScorecardVersion(
			normalized.id(), normalized.versionNo(), normalized.totalScore(), normalized.criteria(),
			normalized.thresholds(), normalized.missingEvidencePolicy(), normalized.sensitiveFeaturePolicy(),
			hasher.scorecardContentHash(normalized));
	}

	private static void validateScorecard(ScorecardVersion scorecard) {
		BigDecimal weight = scorecard.criteria().stream()
			.map(criterion -> criterion.weight())
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (weight.compareTo(new BigDecimal("100")) != 0) {
			throw validation("Scorecard criterion weights must total 100");
		}
		Set<String> criterionCodes = new HashSet<>();
		if (scorecard.criteria().stream().anyMatch(criterion -> !criterionCodes.add(criterion.code()))) {
			throw validation("Scorecard criterion codes must be unique");
		}
		List<RecommendationThreshold> thresholds = scorecard.thresholds();
		if (thresholds.get(0).minimum().compareTo(BigDecimal.ZERO) != 0
				|| thresholds.get(thresholds.size() - 1).maximum().compareTo(new BigDecimal("100")) != 0) {
			throw validation("Recommendation thresholds must cover 0 through 100");
		}
		Set<String> levels = new HashSet<>();
		for (int index = 0; index < thresholds.size(); index++) {
			RecommendationThreshold threshold = thresholds.get(index);
			if (!levels.add(threshold.level()) || threshold.minimum().compareTo(threshold.maximum()) >= 0) {
				throw validation("Recommendation thresholds must be ordered and levels must be unique");
			}
			if (index > 0 && threshold.minimum().compareTo(thresholds.get(index - 1).maximum()) != 0) {
				throw validation("Recommendation thresholds must be continuous and non-overlapping");
			}
		}
	}

	private static void ensurePatchNotEmpty(PatchRequest request) {
		if (request.jobDescription() == null
				&& request.responsibilities() == null
				&& request.requirements() == null
				&& request.hardConstraints() == null
				&& request.scorecard() == null
				&& request.changeSummary() == null) {
			throw validation("Patch must contain at least one position plan field");
		}
	}

	private static PositionPlanVersion withState(
			PositionPlanVersion plan,
			String status,
			long version,
			ResourceRef checkpointRef,
			UserRef approvedBy,
			OffsetDateTime approvedAt,
			OffsetDateTime updatedAt) {
		return new PositionPlanVersion(
			plan.id(), plan.taskId(), plan.versionNo(), status, version, plan.jobDescription(),
			plan.responsibilities(), plan.requirements(), plan.hardConstraints(), plan.scorecard(), plan.generatedBy(),
			plan.basedOnRunId(), plan.knowledgeVersionRefs(), plan.promptVersion(), plan.contentHash(),
			plan.changeSummary(), checkpointRef, approvedBy, approvedAt, plan.createdAt(), updatedAt);
	}

	private static Task updateTaskView(
			Task task,
			String businessStage,
			String executionStatus,
			ResourceRef currentPlanRef,
			OffsetDateTime updatedAt) {
		return new Task(
			task.id(), task.taskNo(), task.title(), task.positionName(), task.organizationRef(), task.owner(),
			task.hiringManager(), task.participants(), task.recruitmentType(), task.headcount(), task.locations(),
			task.priority(), task.targetDate(), businessStage, task.lifecycleStatus(), executionStatus,
			task.version() + 1, task.creationCheckpointRef(), currentPlanRef, task.sourceJobRef(),
			task.createdAt(), updatedAt);
	}

	private static UserRef user(TenantActor actor) {
		return new UserRef(actor.userId(), actor.displayName());
	}

	private static boolean secureEquals(String first, String second) {
		return MessageDigest.isEqual(
			first.getBytes(StandardCharsets.US_ASCII),
			second.getBytes(StandardCharsets.US_ASCII));
	}

	private static void verifyIdempotency(String storedHash, String requestHash) {
		if (!secureEquals(storedHash, requestHash)) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"IDEMPOTENCY_CONFLICT",
				"Idempotency key was already used with a different request");
		}
	}

	private OffsetDateTime now() {
		return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
	}

	private static ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
	}

	private static ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Resource version changed concurrently");
	}

	record AuditContext(UUID requestId, String traceId) {
	}

	record CommandResult<T>(T value, boolean replayed) {
	}
}

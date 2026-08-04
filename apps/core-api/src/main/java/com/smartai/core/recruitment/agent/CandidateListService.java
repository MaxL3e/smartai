package com.smartai.core.recruitment.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartai.core.platform.api.ApiException;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateInvitationPlan;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListConfirmRequest;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListPreview;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListPreviewItem;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListPreviewRequest;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListReviewRequest;
import com.smartai.core.recruitment.agent.CandidateListModels.CandidateListVersion;
import com.smartai.core.recruitment.agent.CandidateListModels.RecommendationCandidate;
import com.smartai.core.recruitment.agent.CandidateListModels.RecommendationCriterion;
import com.smartai.core.recruitment.agent.CandidateListModels.RecommendationReport;
import com.smartai.core.recruitment.agent.MatchingModels.EvidenceRef;
import com.smartai.core.recruitment.agent.MatchingModels.MatchResult;
import com.smartai.core.recruitment.agent.MatchingModels.MatchRun;
import com.smartai.core.recruitment.agent.MatchingModels.TaskCandidate;
import com.smartai.core.recruitment.agent.PositionPlanModels.DecisionRequest;
import com.smartai.core.recruitment.agent.PositionPlanModels.HumanCheckpoint;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Task;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

@Service
class CandidateListService {

	private static final String CREATE_PREVIEW = "G4_CREATE_PREVIEW";
	private static final String REQUEST_REVIEW = "G4_REQUEST_REVIEW";
	private static final String DECIDE_REVIEW = "G4_DECIDE_REVIEW";
	private static final String CONFIRM_LIST = "G4_CONFIRM_LIST";
	private static final Duration PREVIEW_TTL = Duration.ofMinutes(15);

	private final CandidateListRepository repository;
	private final MatchingRepository matchingRepository;
	private final PositionPlanRepository planRepository;
	private final PositionPlanHasher hasher;
	private final Clock clock = Clock.systemUTC();

	CandidateListService(
			CandidateListRepository repository,
			MatchingRepository matchingRepository,
			PositionPlanRepository planRepository,
			PositionPlanHasher hasher) {
		this.repository = repository;
		this.matchingRepository = matchingRepository;
		this.planRepository = planRepository;
		this.hasher = hasher;
	}

	@Transactional
	CommandResult<CandidateListPreview> createPreview(
			TenantActor actor,
			UUID taskId,
			UUID idempotencyKey,
			CandidateListPreviewRequest request,
			AuditContext audit) {
		CandidateListPreviewRequest normalizedRequest = normalize(request);
		String requestHash = hasher.commandHash(CREATE_PREVIEW, taskId, null, normalizedRequest);
		lockTask(actor, taskId);
		var previous = planRepository.findCommand(
			actor.tenantId(), CREATE_PREVIEW, idempotencyKey, CandidateListPreview.class);
		if (previous.isPresent()) {
			verifyIdempotency(previous.get().requestHash(), requestHash);
			return new CommandResult<>(previous.get().response(), true);
		}

		Task task = requireCandidateConfirmationTask(actor, taskId);
		MatchRun run = requireMatchRun(actor, taskId, normalizedRequest.matchRunRef());
		List<CandidateListPreviewItem> items = buildItems(
			actor, taskId, run, normalizedRequest.taskCandidateRefs(), normalizedRequest.selectionNotes());
		String inputHash = previewHash(run, items, normalizedRequest.invitationPlan());
		OffsetDateTime now = now();
		CandidateListPreview preview = new CandidateListPreview(
			UUID.randomUUID(), taskId, 1L, ref("MatchRun", run.id(), run.version()), items,
			normalizedRequest.invitationPlan(), inputHash, now.plus(PREVIEW_TTL));
		repository.insertPreview(actor.tenantId(), preview, task.version(), actor.userId().toString(), now);
		planRepository.insertCommand(
			actor.tenantId(), taskId, preview.id(), CREATE_PREVIEW, idempotencyKey, requestHash, preview,
			1L, actor.userId().toString());
		planRepository.appendAudit(
			actor.tenantId(), user(actor), "CandidateListPreview", preview.id(), "CANDIDATE_LIST_PREVIEW_CREATED",
			audit.requestId(), audit.traceId(), Map.of(
				"taskId", taskId,
				"matchRunId", run.id(),
				"candidateCount", items.size(),
				"inputHash", inputHash,
				"externalImpact", normalizedRequest.invitationPlan().externalImpactSummary()), now);
		return new CommandResult<>(preview, false);
	}

	@Transactional
	CommandResult<HumanCheckpoint> requestReview(
			TenantActor actor,
			UUID taskId,
			long expectedTaskVersion,
			UUID idempotencyKey,
			CandidateListReviewRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(REQUEST_REVIEW, taskId, expectedTaskVersion, request);
		lockTask(actor, taskId);
		var previous = planRepository.findCommand(
			actor.tenantId(), REQUEST_REVIEW, idempotencyKey, HumanCheckpoint.class);
		if (previous.isPresent()) {
			verifyIdempotency(previous.get().requestHash(), requestHash);
			return new CommandResult<>(previous.get().response(), true);
		}

		Task task = requireCandidateConfirmationTask(actor, taskId);
		VersionPrecondition.verify(expectedTaskVersion, task.version());
		CandidateListRepository.StoredPreview stored = requirePreview(actor, taskId, request.previewRef());
		CandidateListPreview preview = stored.preview();
		ensureHash(request.inputHash(), preview.inputHash());
		var existingCheckpoint = planRepository.findCheckpointByResource(
			actor.tenantId(), preview.id(), preview.version(), "CONFIRM_CANDIDATE_LIST");
		if (existingCheckpoint.isPresent()) {
			HumanCheckpoint value = existingCheckpoint.get();
			ensureHash(value.inputHash(), preview.inputHash());
			if (!value.requiredRole().equals(request.requiredRole())
					|| !Objects.equals(value.assigneeUserId(), request.assigneeUserId())) {
				throw new ApiException(
					HttpStatus.CONFLICT,
					"CHECKPOINT_RESOURCE_CHANGED",
					"Candidate-list preview already has a checkpoint with different review ownership");
			}
			planRepository.insertCommand(
				actor.tenantId(), taskId, value.id(), REQUEST_REVIEW, idempotencyKey, requestHash, value,
				value.version(), actor.userId().toString());
			return new CommandResult<>(value, false);
		}
		ensurePreviewCurrent(actor, preview);
		OffsetDateTime now = now();
		OffsetDateTime expiresAt = request.expiresAt() == null ? preview.expiresAt() : request.expiresAt();
		if (!expiresAt.isAfter(now) || expiresAt.isAfter(preview.expiresAt())) {
			throw validation("expiresAt must be in the future and must not outlive the preview");
		}

		UUID checkpointId = UUID.randomUUID();
		HumanCheckpoint checkpoint = new HumanCheckpoint(
			checkpointId, taskId, "CONFIRM_CANDIDATE_LIST", ref("CandidateListPreview", preview.id(), 1L),
			"PENDING", request.requiredRole(), request.assigneeUserId(), preview.inputHash(), 1L,
			"请核对 " + preview.items().size() + " 位候选人的匹配结果、原文证据、系统判断、待核实项和邀请影响。",
			user(actor), now, expiresAt, null, request.comment(), null, null);
		planRepository.insertCheckpoint(actor.tenantId(), checkpoint, null, actor.userId().toString());
		Task updatedTask = updateTask(task, "CANDIDATE_CONFIRMATION", "WAITING_HUMAN", now);
		if (planRepository.updateTask(actor.tenantId(), task, updatedTask, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		planRepository.insertCommand(
			actor.tenantId(), taskId, checkpointId, REQUEST_REVIEW, idempotencyKey, requestHash, checkpoint,
			checkpoint.version(), actor.userId().toString());
		planRepository.appendAudit(
			actor.tenantId(), user(actor), "HumanCheckpoint", checkpointId, "CANDIDATE_LIST_REVIEW_REQUESTED",
			audit.requestId(), audit.traceId(), Map.of(
				"taskId", taskId,
				"previewId", preview.id(),
				"inputHash", preview.inputHash(),
				"candidateCount", preview.items().size()), now);
		return new CommandResult<>(checkpoint, false);
	}

	@Transactional
	CommandResult<HumanCheckpoint> decideReview(
			TenantActor actor,
			UUID checkpointId,
			long expectedCheckpointVersion,
			UUID idempotencyKey,
			DecisionRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(
			DECIDE_REVIEW, checkpointId, expectedCheckpointVersion, request);
		HumanCheckpoint target = planRepository.findCheckpoint(actor.tenantId(), checkpointId)
			.orElseThrow(this::notFound);
		lockTask(actor, target.taskId());
		var previous = planRepository.findCommand(
			actor.tenantId(), DECIDE_REVIEW, idempotencyKey, HumanCheckpoint.class);
		if (previous.isPresent()) {
			verifyIdempotency(previous.get().requestHash(), requestHash);
			return new CommandResult<>(previous.get().response(), true);
		}

		HumanCheckpoint current = planRepository.findCheckpoint(actor.tenantId(), checkpointId)
			.orElseThrow(this::notFound);
		if (!"CONFIRM_CANDIDATE_LIST".equals(current.type())) {
			throw new ApiException(
				HttpStatus.CONFLICT, "CHECKPOINT_TYPE_MISMATCH", "Checkpoint is not a G4 candidate-list gate");
		}
		VersionPrecondition.verify(expectedCheckpointVersion, current.version());
		if ("EXPIRED".equals(current.status())) {
			return new CommandResult<>(current, false);
		}
		if (!"PENDING".equals(current.status())) {
			throw new ApiException(
				HttpStatus.CONFLICT, "CHECKPOINT_ALREADY_DECIDED", "Checkpoint is already terminal");
		}
		OffsetDateTime now = now();
		if (current.expiresAt() != null && !current.expiresAt().isAfter(now)) {
			HumanCheckpoint expired = new HumanCheckpoint(
				current.id(), current.taskId(), current.type(), current.resourceRef(), "EXPIRED", current.requiredRole(),
				current.assigneeUserId(), current.inputHash(), current.version() + 1, current.summary(), current.requestedBy(),
				current.requestedAt(), current.expiresAt(), null, current.comment(), null, now);
			if (planRepository.updateCheckpoint(actor.tenantId(), current, expired, actor.userId().toString()) != 1) {
				throw versionConflict();
			}
			Task task = requireCandidateConfirmationTask(actor, current.taskId());
			Task updatedTask = updateTask(task, "CANDIDATE_CONFIRMATION", "IDLE", now);
			if (planRepository.updateTask(actor.tenantId(), task, updatedTask, actor.userId().toString()) != 1) {
				throw versionConflict();
			}
			planRepository.appendAudit(
				actor.tenantId(), user(actor), "HumanCheckpoint", checkpointId, "CANDIDATE_LIST_REVIEW_EXPIRED",
				audit.requestId(), audit.traceId(), Map.of(
					"taskId", current.taskId(),
					"previewId", current.resourceRef().id(),
					"inputHash", current.inputHash()), now);
			return new CommandResult<>(expired, false);
		}
		ensureHash(request.inputHash(), current.inputHash());
		if (current.assigneeUserId() != null && !current.assigneeUserId().equals(actor.userId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "CHECKPOINT_ASSIGNEE_MISMATCH", "Checkpoint is assigned to another user");
		}
		CandidateListPreview preview = requirePreview(actor, current.taskId(), current.resourceRef()).preview();
		ensureHash(current.inputHash(), preview.inputHash());
		if ("APPROVE".equals(request.decision())) ensurePreviewCurrent(actor, preview);

		String status = switch (request.decision()) {
			case "APPROVE" -> "APPROVED";
			case "REJECT" -> "REJECTED";
			case "CANCEL" -> "CANCELLED";
			default -> throw validation("Unsupported checkpoint decision");
		};
		HumanCheckpoint decided = new HumanCheckpoint(
			current.id(), current.taskId(), current.type(), current.resourceRef(), status, current.requiredRole(),
			current.assigneeUserId(), current.inputHash(), current.version() + 1, current.summary(), current.requestedBy(),
			current.requestedAt(), current.expiresAt(), request.decision(), request.comment(), user(actor), now);
		if (planRepository.updateCheckpoint(actor.tenantId(), current, decided, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		Task task = requireCandidateConfirmationTask(actor, current.taskId());
		Task updatedTask = updateTask(task, "CANDIDATE_CONFIRMATION", "IDLE", now);
		if (planRepository.updateTask(actor.tenantId(), task, updatedTask, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		planRepository.insertCommand(
			actor.tenantId(), current.taskId(), checkpointId, DECIDE_REVIEW, idempotencyKey, requestHash, decided,
			decided.version(), actor.userId().toString());
		planRepository.appendAudit(
			actor.tenantId(), user(actor), "HumanCheckpoint", checkpointId, "CANDIDATE_LIST_REVIEW_DECIDED",
			audit.requestId(), audit.traceId(), Map.of(
				"taskId", current.taskId(),
				"previewId", preview.id(),
				"decision", request.decision(),
				"inputHash", preview.inputHash()), now);
		return new CommandResult<>(decided, false);
	}

	@Transactional
	CommandResult<CandidateListVersion> confirm(
			TenantActor actor,
			UUID taskId,
			long expectedTaskVersion,
			UUID idempotencyKey,
			CandidateListConfirmRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash(CONFIRM_LIST, taskId, expectedTaskVersion, request);
		lockTask(actor, taskId);
		var previous = planRepository.findCommand(
			actor.tenantId(), CONFIRM_LIST, idempotencyKey, CandidateListVersion.class);
		if (previous.isPresent()) {
			verifyIdempotency(previous.get().requestHash(), requestHash);
			return new CommandResult<>(previous.get().response(), true);
		}

		Task task = requireTask(actor, taskId);
		VersionPrecondition.verify(expectedTaskVersion, task.version());
		var existing = repository.findCandidateListByCheckpoint(actor.tenantId(), request.checkpointId());
		if (existing.isPresent()) {
			CandidateListVersion value = existing.get();
			if (!value.taskId().equals(taskId) || !sameRef(value.previewRef(), request.previewRef())) {
				throw new ApiException(HttpStatus.CONFLICT, "CHECKPOINT_RESOURCE_CHANGED", "Checkpoint already confirmed another list");
			}
			planRepository.insertCommand(
				actor.tenantId(), taskId, value.id(), CONFIRM_LIST, idempotencyKey, requestHash, value,
				value.versionNo(), actor.userId().toString());
			return new CommandResult<>(value, false);
		}

		ensureCandidateConfirmationTask(task);
		CandidateListPreview preview = requirePreview(actor, taskId, request.previewRef()).preview();
		ensurePreviewCurrent(actor, preview);
		HumanCheckpoint checkpoint = planRepository.findCheckpoint(actor.tenantId(), request.checkpointId())
			.orElseThrow(this::notFound);
		if (!"CONFIRM_CANDIDATE_LIST".equals(checkpoint.type())
				|| !"APPROVED".equals(checkpoint.status())
				|| !checkpoint.taskId().equals(taskId)
				|| !sameRef(checkpoint.resourceRef(), request.previewRef())
				|| !secureEquals(checkpoint.inputHash(), preview.inputHash())) {
			throw new ApiException(
				HttpStatus.CONFLICT, "CHECKPOINT_RESOURCE_CHANGED", "Approved checkpoint does not match the current preview");
		}

		OffsetDateTime now = now();
		int versionNo = repository.nextCandidateListVersionNo(actor.tenantId(), taskId);
		UUID listId = UUID.randomUUID();
		List<ResourceRef> selectedRefs = preview.items().stream()
			.map(item -> ref(
				"TaskCandidate", item.taskCandidateRef().id(), item.taskCandidateRef().version() + 1))
			.toList();
		ResourceRef checkpointRef = ref("HumanCheckpoint", checkpoint.id(), checkpoint.version());
		String contentHash = hasher.sha256Value(new CandidateListHashContent(
			preview.inputHash(), preview.matchRunRef(), selectedRefs, preview.invitationPlan(), checkpointRef));
		CandidateListVersion list = new CandidateListVersion(
			listId, taskId, versionNo, request.previewRef(), preview.matchRunRef(), selectedRefs,
			preview.invitationPlan(), checkpointRef, user(actor), now, contentHash);
		repository.insertCandidateList(actor.tenantId(), list, actor.userId().toString());
		for (CandidateListPreviewItem item : preview.items()) {
			if (repository.confirmTaskCandidate(
				actor.tenantId(), item.taskCandidateRef(), item.matchResultRef(), list.id(), now,
				actor.userId().toString()) != 1) {
				throw new ApiException(
					HttpStatus.CONFLICT, "CANDIDATE_LIST_PREVIEW_STALE", "A selected candidate changed after preview");
			}
		}

		MatchRun run = requireMatchRun(actor, taskId, preview.matchRunRef());
		RecommendationReport report = buildReport(actor, list, preview, run, now);
		repository.insertReport(actor.tenantId(), report, actor.userId().toString());
		Task updatedTask = updateTask(task, "ONLINE_INTERVIEW", "IDLE", now);
		if (planRepository.updateTask(actor.tenantId(), task, updatedTask, actor.userId().toString()) != 1) {
			throw versionConflict();
		}
		planRepository.insertCommand(
			actor.tenantId(), taskId, list.id(), CONFIRM_LIST, idempotencyKey, requestHash, list,
			list.versionNo(), actor.userId().toString());
		planRepository.appendAudit(
			actor.tenantId(), user(actor), "CandidateListVersion", list.id(), "CANDIDATE_LIST_CONFIRMED",
			audit.requestId(), audit.traceId(), Map.of(
				"taskId", taskId,
				"candidateListVersion", list.versionNo(),
				"candidateCount", list.taskCandidateRefs().size(),
				"recommendationReportId", report.id(),
				"resultingBusinessStage", updatedTask.businessStage(),
				"externalActionCreated", false), now);
		return new CommandResult<>(list, false);
	}

	@Transactional(readOnly = true)
	CandidateListPreview getPreview(TenantActor actor, UUID previewId) {
		return repository.findPreview(actor.tenantId(), previewId)
			.map(CandidateListRepository.StoredPreview::preview)
			.orElseThrow(this::notFound);
	}

	@Transactional(readOnly = true)
	CandidateListVersion getCurrentCandidateList(TenantActor actor, UUID taskId) {
		requireTask(actor, taskId);
		return repository.findCurrentCandidateList(actor.tenantId(), taskId).orElseThrow(this::notFound);
	}

	@Transactional(readOnly = true)
	RecommendationReport getCurrentReport(TenantActor actor, UUID taskId) {
		requireTask(actor, taskId);
		return repository.findCurrentReport(actor.tenantId(), taskId).orElseThrow(this::notFound);
	}

	@Transactional(readOnly = true)
	RecommendationReport getReport(TenantActor actor, UUID reportId) {
		return repository.findReport(actor.tenantId(), reportId).orElseThrow(this::notFound);
	}

	byte[] textReport(RecommendationReport report) {
		StringBuilder text = new StringBuilder();
		text.append("推荐报告 v").append(report.versionNo()).append('\n');
		text.append("任务：").append(report.taskId()).append('\n');
		text.append("候选名单：").append(report.candidateListVersionRef().id()).append('\n');
		text.append("岗位方案：").append(report.positionPlanVersionRef().id()).append('\n');
		text.append("评分卡：").append(report.scorecardVersionRef().id()).append('\n');
		text.append("匹配运行：").append(report.matchRunRef().id()).append("\n\n");
		for (RecommendationCandidate candidate : report.candidates()) {
			text.append(candidate.rank()).append(". ").append(candidate.candidate().displayName())
				.append("  ").append(candidate.totalScore()).append("分  ")
				.append(candidate.recommendationLevel()).append('\n');
			text.append("  选择理由：").append(candidate.selectionReason()).append('\n');
			if (candidate.note() != null) text.append("  HR 备注：").append(candidate.note()).append('\n');
			for (RecommendationCriterion criterion : candidate.criteria()) {
				text.append("  - ").append(criterion.criterionCode()).append("：")
					.append(criterion.weightedScore()).append("分").append('\n');
				if (!criterion.sourceEvidenceRefs().isEmpty()) {
					for (EvidenceRef evidence : criterion.sourceEvidenceRefs()) {
						text.append("    原文证据：").append(evidence.quote()).append('\n');
					}
				}
				text.append("    系统判断：").append(criterion.systemJudgment()).append('\n');
			}
			if (!candidate.needsVerification().isEmpty()) {
				text.append("  待核实项：").append(String.join("；", candidate.needsVerification())).append('\n');
			}
			text.append('\n');
		}
		text.append("报告哈希：").append(report.contentHash()).append('\n');
		return text.toString().getBytes(StandardCharsets.UTF_8);
	}

	private RecommendationReport buildReport(
			TenantActor actor,
			CandidateListVersion list,
			CandidateListPreview preview,
			MatchRun run,
			OffsetDateTime now) {
		Map<UUID, MatchResult> results = matchingRepository.listMatchResults(actor.tenantId(), run.id()).stream()
			.collect(Collectors.toMap(MatchResult::id, Function.identity()));
		Map<UUID, ResourceRef> confirmedTaskCandidateRefs = list.taskCandidateRefs().stream()
			.collect(Collectors.toMap(ResourceRef::id, Function.identity()));
		List<RecommendationCandidate> candidates = preview.items().stream().map(item -> {
			MatchResult result = results.get(item.matchResultRef().id());
			if (result == null) throw new ApiException(
				HttpStatus.CONFLICT, "CANDIDATE_LIST_PREVIEW_STALE", "Frozen match result is no longer available");
			List<RecommendationCriterion> criteria = result.criterionScores().stream()
				.map(criterion -> new RecommendationCriterion(
					criterion.criterionCode(), criterion.weightedScore(), criterion.evidenceStatus(),
					criterion.evidenceRefs().stream()
						.filter(evidence -> evidence.quote() != null && !evidence.quote().isBlank())
						.toList(),
					criterion.explanation()))
				.toList();
			ResourceRef confirmedTaskCandidateRef = confirmedTaskCandidateRefs.get(item.taskCandidateRef().id());
			if (confirmedTaskCandidateRef == null) {
				throw new ApiException(
					HttpStatus.CONFLICT, "CANDIDATE_LIST_PREVIEW_STALE", "Confirmed candidate reference is missing");
			}
			return new RecommendationCandidate(
				confirmedTaskCandidateRef, item.matchResultRef(), result.candidate(), result.resumeVersionRef(),
				result.rank(), result.totalScore(), result.recommendationLevel(),
				result.hardFilterResult().passed(), item.selectionReason(), item.note(), criteria,
				result.needsVerification());
		}).toList();
		int versionNo = repository.nextReportVersionNo(actor.tenantId(), list.taskId());
		UUID reportId = UUID.randomUUID();
		ResourceRef listRef = ref("CandidateListVersion", list.id(), list.versionNo());
		String contentHash = hasher.sha256Value(new RecommendationReportHashContent(
			listRef, run.positionPlanVersionRef(), run.scorecardVersionRef(), preview.matchRunRef(), candidates));
		return new RecommendationReport(
			reportId, list.taskId(), versionNo, listRef, run.positionPlanVersionRef(), run.scorecardVersionRef(),
			preview.matchRunRef(), candidates, user(actor), now, contentHash);
	}

	private void ensurePreviewCurrent(TenantActor actor, CandidateListPreview preview) {
		OffsetDateTime now = now();
		if (!preview.expiresAt().isAfter(now)) {
			throw new ApiException(HttpStatus.GONE, "CANDIDATE_LIST_PREVIEW_EXPIRED", "Candidate-list preview has expired");
		}
		MatchRun run = requireMatchRun(actor, preview.taskId(), preview.matchRunRef());
		List<ResourceRef> refs = preview.items().stream().map(CandidateListPreviewItem::taskCandidateRef).toList();
		Map<UUID, String> notes = preview.items().stream()
			.filter(item -> item.note() != null)
			.collect(Collectors.toMap(item -> item.taskCandidateRef().id(), CandidateListPreviewItem::note));
		List<CandidateListPreviewItem> currentItems = buildItems(actor, preview.taskId(), run, refs, notes);
		String currentHash = previewHash(run, currentItems, preview.invitationPlan());
		if (!secureEquals(currentHash, preview.inputHash())) {
			throw new ApiException(
				HttpStatus.CONFLICT, "CANDIDATE_LIST_PREVIEW_STALE", "Candidate-list preview input has changed");
		}
	}

	private List<CandidateListPreviewItem> buildItems(
			TenantActor actor,
			UUID taskId,
			MatchRun run,
			List<ResourceRef> requestedRefs,
			Map<UUID, String> selectionNotes) {
		Set<UUID> requestedIds = new HashSet<>();
		for (ResourceRef reference : requestedRefs) {
			if (!validRef(reference, "TaskCandidate") || !requestedIds.add(reference.id())) {
				throw validation("taskCandidateRefs must contain unique TaskCandidate references");
			}
		}
		Map<UUID, TaskCandidate> taskCandidates = matchingRepository.listTaskCandidates(actor.tenantId(), taskId)
			.stream().collect(Collectors.toMap(TaskCandidate::id, Function.identity()));
		Map<UUID, MatchResult> resultsByCandidate = matchingRepository.listMatchResults(actor.tenantId(), run.id())
			.stream().collect(Collectors.toMap(result -> result.taskCandidateRef().id(), Function.identity()));
		List<MatchedSelection> selected = new ArrayList<>();
		for (ResourceRef reference : requestedRefs) {
			TaskCandidate taskCandidate = taskCandidates.get(reference.id());
			if (taskCandidate == null || taskCandidate.version() != reference.version()) {
				throw new ApiException(
					HttpStatus.CONFLICT, "CANDIDATE_SELECTION_INVALID", "Selected candidate is not current for this task");
			}
			if (!"GRANTED".equals(taskCandidate.candidate().consentStatus())) {
				throw new ApiException(
					HttpStatus.CONFLICT, "CANDIDATE_CONSENT_NOT_GRANTED",
					"Selected candidate no longer has active processing consent");
			}
			MatchResult result = resultsByCandidate.get(taskCandidate.id());
			if (result == null || taskCandidate.currentMatchResultRef() == null
					|| !taskCandidate.currentMatchResultRef().id().equals(result.id())) {
				throw new ApiException(
					HttpStatus.CONFLICT, "CANDIDATE_SELECTION_INVALID", "Selected candidate is not part of the match run");
			}
			selected.add(new MatchedSelection(taskCandidate, result));
		}
		return selected.stream()
			.sorted(Comparator.comparingInt(value -> value.result().rank()))
			.map(value -> new CandidateListPreviewItem(
				ref("TaskCandidate", value.taskCandidate().id(), value.taskCandidate().version()),
				ref("MatchResult", value.result().id(), value.result().version()),
				value.result().candidate(), selectionReason(value.result()),
				selectionNotes.get(value.taskCandidate().id()), evidence(value.result()),
				value.result().needsVerification()))
			.toList();
	}

	private static String selectionReason(MatchResult result) {
		return result.recommendationLevel() + " / " + result.totalScore().stripTrailingZeros().toPlainString()
			+ " 分；由 HR 纳入本次推荐名单。";
	}

	private static List<EvidenceRef> evidence(MatchResult result) {
		Map<UUID, EvidenceRef> evidence = new LinkedHashMap<>();
		result.hardFilterResult().items().stream()
			.flatMap(item -> item.evidenceRefs().stream())
			.forEach(item -> evidence.putIfAbsent(item.id(), item));
		result.criterionScores().stream()
			.flatMap(item -> item.evidenceRefs().stream())
			.forEach(item -> evidence.putIfAbsent(item.id(), item));
		return evidence.values().stream().limit(100).toList();
	}

	private String previewHash(
			MatchRun run,
			List<CandidateListPreviewItem> items,
			CandidateInvitationPlan invitationPlan) {
		return hasher.sha256Value(new PreviewHashContent(
			ref("MatchRun", run.id(), run.version()), items, invitationPlan));
	}

	private CandidateListPreviewRequest normalize(CandidateListPreviewRequest request) {
		CandidateInvitationPlan plan = request.invitationPlan();
		if (!plan.deadline().isAfter(now())) throw validation("invitationPlan.deadline must be in the future");
		CandidateInvitationPlan normalizedPlan = new CandidateInvitationPlan(
			plan.connectorId(), plan.templateId().strip(), plan.deadline(), plan.channel(),
			plan.messageTemplateId().strip(), plan.externalImpactSummary().strip());
		List<ResourceRef> references = request.taskCandidateRefs().stream()
			.sorted(Comparator.comparing(value -> value.id().toString()))
			.toList();
		Set<UUID> candidateIds = references.stream().map(ResourceRef::id).collect(Collectors.toSet());
		Map<UUID, String> selectionNotes = new LinkedHashMap<>();
		(request.selectionNotes() == null ? Map.<UUID, String>of() : request.selectionNotes()).entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> {
				if (!candidateIds.contains(entry.getKey())) {
					throw validation("selectionNotes may only reference selected task candidates");
				}
				String note = entry.getValue() == null ? null : entry.getValue().strip();
				if (note != null && !note.isBlank()) selectionNotes.put(entry.getKey(), note);
			});
		return new CandidateListPreviewRequest(request.matchRunRef(), references, selectionNotes, normalizedPlan);
	}

	private MatchRun requireMatchRun(TenantActor actor, UUID taskId, ResourceRef reference) {
		if (!validRef(reference, "MatchRun")) throw validation("matchRunRef must identify a MatchRun version");
		MatchRun run = matchingRepository.findMatchRun(actor.tenantId(), reference.id()).orElseThrow(this::notFound);
		if (!run.taskId().equals(taskId) || run.version() != reference.version() || !"SUCCEEDED".equals(run.status())) {
			throw new ApiException(
				HttpStatus.CONFLICT, "MATCH_RUN_VERSION_CONFLICT", "Match run is not the exact successful task run");
		}
		return run;
	}

	private CandidateListRepository.StoredPreview requirePreview(
			TenantActor actor,
			UUID taskId,
			ResourceRef reference) {
		if (!validRef(reference, "CandidateListPreview") || reference.version() != 1L) {
			throw validation("previewRef must identify CandidateListPreview version 1");
		}
		CandidateListRepository.StoredPreview stored = repository.findPreview(actor.tenantId(), reference.id())
			.orElseThrow(this::notFound);
		if (!stored.preview().taskId().equals(taskId)) throw notFound();
		return stored;
	}

	private Task requireCandidateConfirmationTask(TenantActor actor, UUID taskId) {
		Task task = requireTask(actor, taskId);
		ensureCandidateConfirmationTask(task);
		return task;
	}

	private static void ensureCandidateConfirmationTask(Task task) {
		if (!"ACTIVE".equals(task.lifecycleStatus()) || !"CANDIDATE_CONFIRMATION".equals(task.businessStage())) {
			throw new ApiException(
				HttpStatus.CONFLICT, "TASK_STAGE_CONFLICT", "Task must be active and in CANDIDATE_CONFIRMATION");
		}
	}

	private Task requireTask(TenantActor actor, UUID taskId) {
		return planRepository.findTask(actor.tenantId(), taskId).orElseThrow(this::notFound);
	}

	private void lockTask(TenantActor actor, UUID taskId) {
		if (!planRepository.lockTask(actor.tenantId(), taskId)) {
			throw notFound();
		}
	}

	private static CandidateListModels.CandidateListPreviewRequest normalizeReferenceOrder(
			CandidateListModels.CandidateListPreviewRequest request) {
		return request;
	}

	private static Task updateTask(Task task, String stage, String executionStatus, OffsetDateTime now) {
		return new Task(
			task.id(), task.taskNo(), task.title(), task.positionName(), task.organizationRef(), task.owner(),
			task.hiringManager(), task.participants(), task.recruitmentType(), task.headcount(), task.locations(),
			task.priority(), task.targetDate(), stage, task.lifecycleStatus(), executionStatus, task.version() + 1,
			task.creationCheckpointRef(), task.currentPlanVersionRef(), task.sourceJobRef(), task.createdAt(), now);
	}

	private static boolean validRef(ResourceRef reference, String type) {
		return reference != null && reference.id() != null && reference.version() > 0 && type.equals(reference.type());
	}

	private static boolean sameRef(ResourceRef first, ResourceRef second) {
		return first != null && second != null && first.id().equals(second.id())
			&& first.version() == second.version() && first.type().equals(second.type());
	}

	private static ResourceRef ref(String type, UUID id, long version) {
		return new ResourceRef(type, id, version);
	}

	private static UserRef user(TenantActor actor) {
		return new UserRef(actor.userId(), actor.displayName());
	}

	private static void ensureHash(String supplied, String expected) {
		if (!secureEquals(supplied, expected)) {
			throw new ApiException(
				HttpStatus.CONFLICT, "CONFIRMATION_INPUT_CHANGED", "Input hash does not match the frozen preview");
		}
	}

	private static void verifyIdempotency(String previousHash, String requestHash) {
		if (!secureEquals(previousHash, requestHash)) {
			throw new ApiException(
				HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was used with a different request");
		}
	}

	private static boolean secureEquals(String first, String second) {
		return first != null && second != null && MessageDigest.isEqual(
			first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
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
		return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Resource was updated concurrently");
	}

	record AuditContext(UUID requestId, String traceId) {
	}

	record CommandResult<T>(T value, boolean replayed) {
	}

	private record MatchedSelection(TaskCandidate taskCandidate, MatchResult result) {
	}

	private record PreviewHashContent(
			ResourceRef matchRunRef,
			List<CandidateListPreviewItem> items,
			CandidateInvitationPlan invitationPlan) {
	}

	private record CandidateListHashContent(
			String previewHash,
			ResourceRef matchRunRef,
			List<ResourceRef> taskCandidateRefs,
			CandidateInvitationPlan invitationPlan,
			ResourceRef checkpointRef) {
	}

	private record RecommendationReportHashContent(
			ResourceRef candidateListVersionRef,
			ResourceRef positionPlanVersionRef,
			ResourceRef scorecardVersionRef,
			ResourceRef matchRunRef,
			List<RecommendationCandidate> candidates) {
	}
}

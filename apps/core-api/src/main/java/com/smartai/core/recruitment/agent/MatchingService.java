package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartai.core.platform.api.ApiException;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateFacts;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateFilters;
import com.smartai.core.recruitment.agent.MatchingModels.CriterionScore;
import com.smartai.core.recruitment.agent.MatchingModels.EvidenceRef;
import com.smartai.core.recruitment.agent.MatchingModels.EvidenceSourceLocator;
import com.smartai.core.recruitment.agent.MatchingModels.HardFilterItem;
import com.smartai.core.recruitment.agent.MatchingModels.HardFilterResult;
import com.smartai.core.recruitment.agent.MatchingModels.MatchResult;
import com.smartai.core.recruitment.agent.MatchingModels.MatchRun;
import com.smartai.core.recruitment.agent.MatchingModels.MatchRunCreateRequest;
import com.smartai.core.recruitment.agent.MatchingModels.MatchRunMetrics;
import com.smartai.core.recruitment.agent.MatchingModels.NormalizedCandidate;
import com.smartai.core.recruitment.agent.MatchingModels.ResumeSection;
import com.smartai.core.recruitment.agent.MatchingModels.TaskCandidate;
import com.smartai.core.recruitment.agent.PositionPlanModels.HardConstraint;
import com.smartai.core.recruitment.agent.PositionPlanModels.PositionPlanVersion;
import com.smartai.core.recruitment.agent.PositionPlanModels.RecommendationThreshold;
import com.smartai.core.recruitment.agent.PositionPlanModels.ScoreCriterion;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Task;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

@Service
class MatchingService {

	static final String GENERATOR_KIND = "DETERMINISTIC_RULES";
	static final String SEARCH_INDEX_VERSION = "normalized-resume-v1";
	static final String PIPELINE_VERSION = "deterministic-match-v1";

	private final MatchingRepository repository;
	private final PositionPlanRepository planRepository;
	private final PositionPlanHasher hasher;
	private final Clock clock = Clock.systemUTC();

	MatchingService(
			MatchingRepository repository,
			PositionPlanRepository planRepository,
			PositionPlanHasher hasher) {
		this.repository = repository;
		this.planRepository = planRepository;
		this.hasher = hasher;
	}

	@Transactional
	CommandResult<MatchRun> createRun(
			TenantActor actor,
			UUID taskId,
			UUID idempotencyKey,
			MatchRunCreateRequest request,
			AuditContext audit) {
		String requestHash = hasher.commandHash("CREATE_MATCH_RUN", taskId, null, request);
		var previous = repository.findMatchRunByIdempotency(actor.tenantId(), idempotencyKey);
		if (previous.isPresent()) {
			verifyIdempotency(previous.get().requestHash(), requestHash);
			return new CommandResult<>(previous.get().response(), true);
		}

		Task task = requireTask(actor, taskId);
		if (!Set.of("TALENT_SEARCH", "CANDIDATE_CONFIRMATION").contains(task.businessStage())) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"TASK_STAGE_CONFLICT",
				"Recruitment task must have an approved position plan before matching");
		}
		PositionPlanVersion plan = requireApprovedPlan(actor, taskId, request);
		OffsetDateTime startedAt = now();
		MatchRun running = new MatchRun(
			UUID.randomUUID(), taskId, request.positionPlanVersionRef(), request.scorecardVersionRef(),
			request.candidateScope(), SEARCH_INDEX_VERSION, PIPELINE_VERSION, GENERATOR_KIND, "RUNNING", 1L,
			new MatchRunMetrics(0, 0, 0, 0, 0, 0, null), user(actor), startedAt, startedAt, null, null);
		repository.insertMatchRun(
			actor.tenantId(), running, plan.id(), plan.scorecard().id(), idempotencyKey, requestHash,
			actor.userId().toString());

		List<NormalizedCandidate> allCandidates = repository.listLatestCandidates(actor.tenantId());
		List<NormalizedCandidate> scoped = allCandidates.stream()
			.filter(candidate -> "GRANTED".equals(candidate.candidate().consentStatus()))
			.filter(candidate -> request.candidateScope().connectorIds().contains(candidate.connectorId()))
			.filter(candidate -> !candidate.sourceUpdatedAt().isAfter(request.candidateScope().dataCutoffAt()))
			.filter(scopeFilter(request.candidateScope().filters()))
			.limit(request.candidateScope().maximumCandidates() == null
				? Long.MAX_VALUE : request.candidateScope().maximumCandidates())
			.toList();

		List<ScoredCandidate> scored = scoped.stream()
			.map(candidate -> score(plan, candidate, request.minimumRecommendationScore()))
			.sorted(Comparator.comparing(ScoredCandidate::totalScore).reversed()
				.thenComparing(value -> value.candidate().candidate().candidateNo()))
			.toList();

		int rank = 0;
		for (ScoredCandidate item : scored) {
			rank += 1;
			TaskCandidate taskCandidate = repository.findOrInsertTaskCandidate(
				actor.tenantId(), taskId, item.candidate(), startedAt, actor.userId().toString());
			MatchResult result = new MatchResult(
				UUID.randomUUID(), running.id(), new ResourceRef("TaskCandidate", taskCandidate.id(), taskCandidate.version()),
				item.candidate().candidate(), item.candidate().resumeVersionRef(), request.scorecardVersionRef(), rank,
				item.totalScore(), item.recommendationLevel(), GENERATOR_KIND, item.hardFilterResult(), item.criterionScores(),
				item.confidence(), "UNREVIEWED", 1L, item.needsVerification());
			repository.insertMatchResult(
				actor.tenantId(), item.candidate().candidate().id(), item.candidate().resumeVersionRef().id(),
				result, startedAt, actor.userId().toString());
			if (repository.updateTaskCandidateCurrent(
				actor.tenantId(), taskCandidate, result.id(), startedAt, actor.userId().toString()) != 1) {
				throw versionConflict();
			}
		}

		OffsetDateTime finishedAt = now();
		int hardFiltered = (int) scored.stream().filter(value -> !value.hardFilterResult().passed()).count();
		MatchRun completed = new MatchRun(
			running.id(), running.taskId(), running.positionPlanVersionRef(), running.scorecardVersionRef(),
			running.candidateScope(), running.searchIndexVersion(), running.pipelineVersion(), running.generatorKind(), "SUCCEEDED",
			running.version() + 1,
			new MatchRunMetrics(
				allCandidates.size(), hardFiltered, scoped.size(), scoped.size(), scored.size(), 0,
				Duration.between(startedAt, finishedAt).toMillis()),
			running.requestedBy(), running.createdAt(), running.startedAt(), finishedAt, null);
		if (repository.updateMatchRun(actor.tenantId(), running, completed, actor.userId().toString()) != 1) {
			throw versionConflict();
		}

		if (!scored.isEmpty() && !"CANDIDATE_CONFIRMATION".equals(task.businessStage())) {
			Task updatedTask = updateTaskStage(task, "CANDIDATE_CONFIRMATION", finishedAt);
			if (planRepository.updateTask(actor.tenantId(), task, updatedTask, actor.userId().toString()) != 1) {
				throw versionConflict();
			}
		}
		Map<String, Object> auditPayload = new LinkedHashMap<>();
		auditPayload.put("taskId", taskId);
		auditPayload.put("matchRunId", completed.id());
		auditPayload.put("generatorKind", GENERATOR_KIND);
		auditPayload.put("scanned", completed.metrics().scanned());
		auditPayload.put("scored", completed.metrics().scored());
		auditPayload.put("hardFiltered", completed.metrics().hardFiltered());
		auditPayload.put("modelInvocationId", null);
		planRepository.appendAudit(
			actor.tenantId(), user(actor), "MatchRun", completed.id(), "DETERMINISTIC_MATCH_COMPLETED",
			audit.requestId(), audit.traceId(), auditPayload, finishedAt);
		return new CommandResult<>(completed, false);
	}

	@Transactional(readOnly = true)
	MatchRun getRun(TenantActor actor, UUID runId) {
		return repository.findMatchRun(actor.tenantId(), runId).orElseThrow(MatchingService::notFound);
	}

	@Transactional(readOnly = true)
	List<MatchResult> listResults(TenantActor actor, UUID runId) {
		getRun(actor, runId);
		return repository.listMatchResults(actor.tenantId(), runId);
	}

	@Transactional(readOnly = true)
	MatchResult getResult(TenantActor actor, UUID resultId) {
		return repository.findMatchResult(actor.tenantId(), resultId).orElseThrow(MatchingService::notFound);
	}

	@Transactional(readOnly = true)
	List<TaskCandidate> listTaskCandidates(TenantActor actor, UUID taskId) {
		requireTask(actor, taskId);
		return repository.listTaskCandidates(actor.tenantId(), taskId);
	}

	private PositionPlanVersion requireApprovedPlan(
			TenantActor actor,
			UUID taskId,
			MatchRunCreateRequest request) {
		ResourceRef planRef = request.positionPlanVersionRef();
		if (!"PositionPlanVersion".equals(planRef.type())) {
			throw validation("positionPlanVersionRef.type must be PositionPlanVersion");
		}
		PositionPlanVersion plan = planRepository.findPlan(actor.tenantId(), planRef.id()).orElseThrow(MatchingService::notFound);
		if (!plan.taskId().equals(taskId) || plan.version() != planRef.version()) {
			throw new ApiException(HttpStatus.CONFLICT, "PLAN_VERSION_MISMATCH", "Position plan reference is stale or belongs to another task");
		}
		if (!"APPROVED".equals(plan.status())) {
			throw new ApiException(HttpStatus.CONFLICT, "POSITION_PLAN_NOT_APPROVED", "Position plan must be approved before matching");
		}
		ResourceRef scorecardRef = request.scorecardVersionRef();
		if (!"ScorecardVersion".equals(scorecardRef.type())
				|| !plan.scorecard().id().equals(scorecardRef.id())
				|| plan.scorecard().versionNo() != scorecardRef.version()) {
			throw new ApiException(HttpStatus.CONFLICT, "SCORECARD_VERSION_MISMATCH", "Scorecard reference does not match the approved position plan");
		}
		return plan;
	}

	private Predicate<NormalizedCandidate> scopeFilter(CandidateFilters filters) {
		return candidate -> {
			CandidateFacts facts = candidate.facts();
			if (filters.updatedAfter() != null && candidate.sourceUpdatedAt().isBefore(filters.updatedAfter())) return false;
			if (filters.minimumExperienceYears() != null
					&& (facts.experienceYears() == null || facts.experienceYears().compareTo(filters.minimumExperienceYears()) < 0)) {
				return false;
			}
			if (filters.locations() != null && !filters.locations().isEmpty()
					&& filters.locations().stream().noneMatch(value -> equalText(value, facts.location()))) return false;
			if (filters.educationLevels() != null && !filters.educationLevels().isEmpty()
					&& filters.educationLevels().stream().noneMatch(value -> equalText(value, facts.educationLevel()))) return false;
			String searchable = searchableText(candidate);
			return filters.keywords() == null || filters.keywords().stream()
				.allMatch(keyword -> searchable.contains(keyword.toLowerCase(Locale.ROOT).strip()));
		};
	}

	private ScoredCandidate score(
			PositionPlanVersion plan,
			NormalizedCandidate candidate,
			BigDecimal minimumRecommendationScore) {
		HardFilterResult hardFilter = hardFilter(plan.hardConstraints(), candidate);
		if (!hardFilter.passed()) {
			List<CriterionScore> zeroScores = plan.scorecard().criteria().stream()
				.sorted(Comparator.comparing(ScoreCriterion::displayOrder))
				.map(criterion -> new CriterionScore(
					criterion.code(), BigDecimal.ZERO, BigDecimal.ZERO, calculationVersion(criterion),
					"INSUFFICIENT", List.of(), "候选人未通过岗位硬条件，未进入评分。"))
				.toList();
			return new ScoredCandidate(
				candidate, BigDecimal.ZERO.setScale(2), "NOT_RECOMMENDED", hardFilter, zeroScores,
				BigDecimal.ONE, List.of("未通过岗位硬条件"));
		}

		List<CriterionScore> criterionScores = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		List<String> needsVerification = new ArrayList<>();
		for (ScoreCriterion criterion : plan.scorecard().criteria().stream()
				.sorted(Comparator.comparing(ScoreCriterion::displayOrder)).toList()) {
			CriterionScore score = scoreCriterion(plan, criterion, candidate);
			criterionScores.add(score);
			total = total.add(score.weightedScore());
			if (score.evidenceRefs().isEmpty()) needsVerification.add(criterion.name() + "缺少可定位证据");
		}
		total = total.setScale(2, RoundingMode.HALF_UP).min(new BigDecimal("100.00"));
		String level = recommendation(plan.scorecard().thresholds(), total);
		if (minimumRecommendationScore != null && total.compareTo(minimumRecommendationScore) < 0) {
			level = "NOT_RECOMMENDED";
			needsVerification.add("低于本次匹配最低推荐分 " + minimumRecommendationScore.stripTrailingZeros().toPlainString());
		}
		long evidenced = criterionScores.stream().filter(value -> !value.evidenceRefs().isEmpty()).count();
		BigDecimal confidence = BigDecimal.valueOf(evidenced)
			.divide(BigDecimal.valueOf(Math.max(1, criterionScores.size())), 2, RoundingMode.HALF_UP);
		return new ScoredCandidate(
			candidate, total, level, hardFilter, List.copyOf(criterionScores), confidence,
			List.copyOf(needsVerification));
	}

	private CriterionScore scoreCriterion(
			PositionPlanVersion plan,
			ScoreCriterion criterion,
			NormalizedCandidate candidate) {
		if (!"PRESENCE".equals(criterion.scoringRule().type())) {
			return new CriterionScore(
				criterion.code(), BigDecimal.ZERO, BigDecimal.ZERO, calculationVersion(criterion),
				"INSUFFICIENT", List.of(), "当前确定性引擎仅执行 PRESENCE 规则。请人工复核。" );
		}
		List<String> preferredSections = switch (criterion.code()) {
			case "DOMAIN_EXPERIENCE" -> List.of("EXPERIENCE", "PROJECT", "SUMMARY");
			case "CORE_SKILLS" -> List.of("SKILLS", "EXPERIENCE", "PROJECT");
			case "ROLE_FIT" -> List.of("SUMMARY", "EXPERIENCE", "PROJECT", "EDUCATION");
			default -> List.of("SUMMARY", "EXPERIENCE", "SKILLS", "PROJECT", "EDUCATION");
		};
		Optional<ResumeSection> section = preferredSections.stream()
			.map(code -> candidate.sections().stream().filter(value -> code.equals(value.code())).findFirst())
			.flatMap(Optional::stream)
			.filter(value -> !value.text().isBlank())
			.findFirst();
		if (section.isEmpty()) {
			return new CriterionScore(
				criterion.code(), BigDecimal.ZERO, BigDecimal.ZERO, calculationVersion(criterion),
				"INSUFFICIENT", List.of(), "简历中没有满足该维度的可定位信息。" );
		}

		String evidenceText = section.get().text();
		String search = evidenceText.toLowerCase(Locale.ROOT);
		int matches = keywords(plan, criterion).stream().mapToInt(keyword -> search.contains(keyword) ? 1 : 0).sum();
		BigDecimal rawScore = switch (criterion.code()) {
			case "CORE_SKILLS" -> BigDecimal.valueOf(Math.min(100, 65 + candidate.facts().skills().size() * 5 + matches * 5));
			case "DOMAIN_EXPERIENCE" -> BigDecimal.valueOf(Math.min(100, 70 + matches * 10));
			case "ROLE_FIT" -> BigDecimal.valueOf(Math.min(100, 75 + matches * 5));
			default -> BigDecimal.valueOf(Math.min(100, 70 + matches * 5));
		};
		if (criterion.capScore() != null) rawScore = rawScore.min(criterion.capScore());
		BigDecimal weighted = rawScore.multiply(criterion.weight())
			.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
		EvidenceRef evidence = evidence(candidate, section.get());
		return new CriterionScore(
			criterion.code(), rawScore.setScale(2), weighted, calculationVersion(criterion), "SUPPORTED",
			List.of(evidence), "按已批准评分卡权重计算，证据来自标准化简历的 " + section.get().code() + " 分段。" );
	}

	private HardFilterResult hardFilter(List<HardConstraint> constraints, NormalizedCandidate candidate) {
		List<HardFilterItem> items = constraints.stream().map(constraint -> {
			Object actual = factValue(candidate.facts(), constraint.field());
			boolean passed = compare(actual, constraint.operator(), constraint.value());
			Optional<ResumeSection> source = sectionForField(candidate.sections(), constraint.field());
			List<EvidenceRef> evidence = source.map(value -> List.of(evidence(candidate, value))).orElseGet(List::of);
			String reason = passed
				? "满足硬条件：" + constraint.name()
				: "不满足硬条件：" + constraint.name() + "（期望 " + constraint.value() + "，实际 " + actual + "）";
			return new HardFilterItem(constraint.code(), passed, reason, evidence);
		}).toList();
		return new HardFilterResult(items.stream().allMatch(HardFilterItem::passed), items);
	}

	private EvidenceRef evidence(NormalizedCandidate candidate, ResumeSection section) {
		String quote = section.text().length() > 500 ? section.text().substring(0, 500) : section.text();
		return new EvidenceRef(
			UUID.randomUUID(), "RESUME", candidate.resumeVersionRef(),
			new EvidenceSourceLocator(null, section.code(), null, 0, quote.length(), null, null),
			quote, hasher.sha256Value(quote), BigDecimal.ONE, null, null, null);
	}

	private static Optional<ResumeSection> sectionForField(List<ResumeSection> sections, String field) {
		String code = switch (field) {
			case "location" -> "LOCATION";
			case "educationLevel" -> "EDUCATION";
			case "skills" -> "SKILLS";
			default -> "EXPERIENCE";
		};
		return sections.stream().filter(value -> code.equals(value.code())).findFirst();
	}

	private static Object factValue(CandidateFacts facts, String field) {
		return switch (field) {
			case "location" -> facts.location();
			case "experienceYears" -> facts.experienceYears();
			case "educationLevel" -> facts.educationLevel();
			case "skills" -> facts.skills();
			default -> null;
		};
	}

	private static boolean compare(Object actual, String operator, Object expected) {
		return switch (operator) {
			case "EXISTS" -> actual != null && (!(actual instanceof List<?> values) || !values.isEmpty());
			case "EQ" -> equalValue(actual, expected);
			case "IN" -> expected instanceof List<?> values && values.stream().anyMatch(value -> equalValue(actual, value));
			case "GTE" -> decimal(actual).flatMap(left -> decimal(expected).map(right -> left.compareTo(right) >= 0)).orElse(false);
			case "LTE" -> decimal(actual).flatMap(left -> decimal(expected).map(right -> left.compareTo(right) <= 0)).orElse(false);
			default -> false;
		};
	}

	private static boolean equalValue(Object actual, Object expected) {
		if (actual instanceof List<?> values) return values.stream().anyMatch(value -> equalValue(value, expected));
		return actual != null && expected != null && equalText(actual.toString(), expected.toString());
	}

	private static Optional<BigDecimal> decimal(Object value) {
		if (value instanceof BigDecimal decimal) return Optional.of(decimal);
		if (value instanceof Number number) return Optional.of(new BigDecimal(number.toString()));
		try {
			return value == null ? Optional.empty() : Optional.of(new BigDecimal(value.toString()));
		}
		catch (NumberFormatException exception) {
			return Optional.empty();
		}
	}

	private static List<String> keywords(PositionPlanVersion plan, ScoreCriterion criterion) {
		String combined = String.join(" ", plan.requirements()) + " " + plan.jobDescription() + " " + criterion.name();
		return List.of(combined.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}+#.]+"))
			.stream().filter(value -> value.length() >= 2).distinct().limit(12).toList();
	}

	private static String searchableText(NormalizedCandidate candidate) {
		return (candidate.sections().stream().map(ResumeSection::text).reduce("", (left, right) -> left + " " + right)
			+ " " + String.join(" ", candidate.facts().skills())).toLowerCase(Locale.ROOT);
	}

	private static String recommendation(List<RecommendationThreshold> thresholds, BigDecimal total) {
		return thresholds.stream()
			.filter(threshold -> total.compareTo(threshold.minimum()) >= 0
				&& (total.compareTo(new BigDecimal("100")) == 0
					? total.compareTo(threshold.maximum()) <= 0
					: total.compareTo(threshold.maximum()) < 0))
			.map(RecommendationThreshold::level)
			.findFirst()
			.orElse("NOT_RECOMMENDED");
	}

	private static String calculationVersion(ScoreCriterion criterion) {
		return criterion.scoringRule().calculationVersion() == null
			? PIPELINE_VERSION : criterion.scoringRule().calculationVersion();
	}

	private Task requireTask(TenantActor actor, UUID taskId) {
		return planRepository.findTask(actor.tenantId(), taskId).orElseThrow(MatchingService::notFound);
	}

	private static Task updateTaskStage(Task task, String stage, OffsetDateTime now) {
		return new Task(
			task.id(), task.taskNo(), task.title(), task.positionName(), task.organizationRef(), task.owner(),
			task.hiringManager(), task.participants(), task.recruitmentType(), task.headcount(), task.locations(),
			task.priority(), task.targetDate(), stage, task.lifecycleStatus(), "IDLE", task.version() + 1,
			task.creationCheckpointRef(), task.currentPlanVersionRef(), task.sourceJobRef(), task.createdAt(), now);
	}

	private static boolean equalText(String first, String second) {
		return first != null && second != null && first.strip().equalsIgnoreCase(second.strip());
	}

	private static void verifyIdempotency(String previousHash, String requestHash) {
		if (!MessageDigest.isEqual(
				previousHash.getBytes(StandardCharsets.US_ASCII), requestHash.getBytes(StandardCharsets.US_ASCII))) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"IDEMPOTENCY_CONFLICT",
				"Idempotency key was already used with a different request");
		}
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Matching resource was not found");
	}

	private static ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
	}

	private static ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Resource was updated concurrently");
	}

	private static UserRef user(TenantActor actor) {
		return new UserRef(actor.userId(), actor.displayName());
	}

	private OffsetDateTime now() {
		return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	record AuditContext(UUID requestId, String traceId) {
	}

	record CommandResult<T>(T value, boolean replayed) {
	}

	private record ScoredCandidate(
			NormalizedCandidate candidate,
			BigDecimal totalScore,
			String recommendationLevel,
			HardFilterResult hardFilterResult,
			List<CriterionScore> criterionScores,
			BigDecimal confidence,
			List<String> needsVerification) {
	}
}

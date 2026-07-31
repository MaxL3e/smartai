package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartai.core.platform.api.ApiException;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateFacts;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateInputReceipt;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateInputRequest;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateSummary;
import com.smartai.core.recruitment.agent.MatchingModels.NormalizedCandidate;
import com.smartai.core.recruitment.agent.MatchingModels.ResumeSection;
import com.smartai.core.recruitment.agent.MatchingRepository.CandidateRow;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

@Service
class CandidateNormalizationService {

	static final String NORMALIZER_KIND = "DETERMINISTIC_NORMALIZER";

	private final MatchingRepository repository;
	private final PositionPlanRepository auditRepository;
	private final PositionPlanHasher hasher;
	private final Clock clock = Clock.systemUTC();

	CandidateNormalizationService(
			MatchingRepository repository,
			PositionPlanRepository auditRepository,
			PositionPlanHasher hasher) {
		this.repository = repository;
		this.auditRepository = auditRepository;
		this.hasher = hasher;
	}

	@Transactional
	CommandResult<CandidateInputReceipt> normalize(
			TenantActor actor,
			UUID idempotencyKey,
			CandidateInputRequest request,
			AuditContext audit) {
		ensureTenant(actor.tenantId());
		NormalizedInput normalized = normalize(request);
		String requestHash = hasher.sha256Value(normalized);
		var previous = repository.findCandidateCommand(actor.tenantId(), idempotencyKey);
		if (previous.isPresent()) {
			verifyIdempotency(previous.get().requestHash(), requestHash);
			return new CommandResult<>(previous.get().response(), true);
		}

		OffsetDateTime now = now();
		CandidateRow candidate = repository.findCandidateByExternal(
			actor.tenantId(), request.sourceSystem(), request.externalCandidateId())
			.map(current -> updateCandidate(actor, current, normalized, now))
			.orElseGet(() -> createCandidate(actor, normalized, now));

		var existingResume = repository.findResumeBySourceVersion(
			actor.tenantId(), candidate.id(), request.sourceVersion());
		if (existingResume.isPresent()) {
			if (!secureEquals(existingResume.get().contentHash(), normalized.contentHash())) {
				throw new ApiException(
					HttpStatus.CONFLICT,
					"SOURCE_VERSION_CONFLICT",
					"sourceVersion already exists with different normalized resume content");
			}
			CandidateInputReceipt receipt = receipt(existingResume.get(), now);
			repository.insertCandidateCommand(
				actor.tenantId(), idempotencyKey, requestHash, receipt, candidate.version(), now,
				actor.userId().toString());
			return new CommandResult<>(receipt, false);
		}

		int resumeVersionNo = repository.nextResumeVersionNo(actor.tenantId(), candidate.id());
		UUID resumeId = UUID.randomUUID();
		repository.insertResume(
			actor.tenantId(), resumeId, candidate.id(), resumeVersionNo, request.sourceVersion(),
			normalized.sections(), normalized.facts(), normalized.contentHash(), request.sourceUpdatedAt(), now,
			actor.userId().toString());
		NormalizedCandidate stored = repository.findResumeBySourceVersion(
			actor.tenantId(), candidate.id(), request.sourceVersion())
			.orElseThrow(() -> new IllegalStateException("Normalized resume was not persisted"));
		CandidateInputReceipt receipt = receipt(stored, now);
		repository.insertCandidateCommand(
			actor.tenantId(), idempotencyKey, requestHash, receipt, candidate.version(), now,
			actor.userId().toString());
		auditRepository.appendAudit(
			actor.tenantId(), user(actor), "ResumeVersion", resumeId, "CANDIDATE_INPUT_NORMALIZED",
			audit.requestId(), audit.traceId(),
			Map.of(
				"candidateId", candidate.id(),
				"connectorId", request.connectorId(),
				"sourceType", request.sourceType(),
				"normalizerKind", NORMALIZER_KIND,
				"contentHash", normalized.contentHash()),
			now);
		return new CommandResult<>(receipt, false);
	}

	private CandidateRow createCandidate(TenantActor actor, NormalizedInput input, OffsetDateTime now) {
		UUID id = UUID.randomUUID();
		String candidateNo = input.request().candidateNo() == null || input.request().candidateNo().isBlank()
			? "C-" + id.toString().substring(0, 8).toUpperCase()
			: input.request().candidateNo().strip();
		CandidateRow candidate = new CandidateRow(
			id, candidateNo, input.request().displayName().strip(), input.request().consentStatus(),
			input.request().sourceType(), input.request().sourceSystem(), input.request().externalCandidateId(),
			input.request().sourceVersion(), input.request().connectorId(), input.request().sourceApplicationRef(),
			1L, now, now);
		repository.insertCandidate(actor.tenantId(), candidate, actor.userId().toString());
		return candidate;
	}

	private CandidateRow updateCandidate(
			TenantActor actor,
			CandidateRow current,
			NormalizedInput input,
			OffsetDateTime now) {
		CandidateRow updated = new CandidateRow(
			current.id(), current.candidateNo(), input.request().displayName().strip(), input.request().consentStatus(),
			input.request().sourceType(), current.sourceSystem(), current.externalCandidateId(),
			input.request().sourceVersion(), input.request().connectorId(), input.request().sourceApplicationRef(),
			current.version() + 1, current.createdAt(), now);
		if (repository.updateCandidate(actor.tenantId(), current, updated, actor.userId().toString()) != 1) {
			throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Candidate was updated concurrently");
		}
		return updated;
	}

	private NormalizedInput normalize(CandidateInputRequest request) {
		List<ResumeSection> sections = request.sections().stream()
			.map(section -> new ResumeSection(section.code(), normalizeText(section.text())))
			.toList();
		List<String> skills = request.facts().skills() == null
			? List.of()
			: List.copyOf(new LinkedHashSet<>(request.facts().skills().stream()
				.map(CandidateNormalizationService::normalizeText)
				.filter(value -> !value.isBlank())
				.toList()));
		CandidateFacts facts = new CandidateFacts(
			normalizeNullable(request.facts().location()), normalizeDecimal(request.facts().experienceYears()),
			normalizeNullable(request.facts().educationLevel()), skills);
		String contentHash = hasher.sha256Value(new ResumeContent(sections, facts));
		return new NormalizedInput(request, sections, facts, contentHash);
	}

	private static BigDecimal normalizeDecimal(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros();
	}

	private static String normalizeNullable(String value) {
		return value == null ? null : normalizeText(value);
	}

	private static String normalizeText(String value) {
		return value == null ? "" : value.replaceAll("[\\t\\x0B\\f\\r ]+", " ").strip();
	}

	private static CandidateInputReceipt receipt(NormalizedCandidate candidate, OffsetDateTime normalizedAt) {
		return new CandidateInputReceipt(
			candidate.candidate(), candidate.resumeVersionRef(), candidate.connectorId(), candidate.sourceType(),
			candidate.contentHash(), NORMALIZER_KIND, normalizedAt);
	}

	private void ensureTenant(UUID tenantId) {
		if (!repository.tenantExists(tenantId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant was not found or is inactive");
		}
	}

	private static void verifyIdempotency(String previousHash, String requestHash) {
		if (!secureEquals(previousHash, requestHash)) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"IDEMPOTENCY_CONFLICT",
				"Idempotency key was already used with a different request");
		}
	}

	private static boolean secureEquals(String first, String second) {
		return MessageDigest.isEqual(
			first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
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

	private record ResumeContent(List<ResumeSection> sections, CandidateFacts facts) {
	}

	private record NormalizedInput(
			CandidateInputRequest request,
			List<ResumeSection> sections,
			CandidateFacts facts,
			String contentHash) {
	}
}

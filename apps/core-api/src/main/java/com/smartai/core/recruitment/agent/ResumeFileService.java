package com.smartai.core.recruitment.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartai.core.platform.api.ApiException;
import com.smartai.core.recruitment.agent.CandidateNormalizationService.AuditContext;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateFacts;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateInputReceipt;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateInputRequest;
import com.smartai.core.recruitment.agent.MatchingModels.ResumeSection;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;
import com.smartai.core.recruitment.agent.ResumeFileModels.PageResult;
import com.smartai.core.recruitment.agent.ResumeFileModels.ParsedResumeProfile;
import com.smartai.core.recruitment.agent.ResumeFileModels.ParserOutcome;
import com.smartai.core.recruitment.agent.ResumeFileModels.ResumeFile;
import com.smartai.core.recruitment.agent.ResumeFileRepository.DocumentRow;

@Service
class ResumeFileService {

	private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
	private static final int MAX_NORMALIZED_SECTION_LENGTH = 20_000;
	private static final String DEFAULT_SOURCE_SYSTEM = "smartai.resume-library";
	// Standalone uploads are explicit tenant-authorized imports; external connectors retain their own consent state.
	private static final String STANDALONE_IMPORT_CONSENT_STATUS = "GRANTED";
	private static final UUID STANDALONE_CONNECTOR_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000301");
	private static final Pattern SOURCE_SYSTEM = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,63}$");
	private static final Pattern PARSE_STATUS = Pattern.compile("^(PARSED|PARSE_FAILED)$");

	private final ResumeFileRepository repository;
	private final ResumeFileParser parser;
	private final CandidateNormalizationService normalizationService;
	private final PositionPlanRepository auditRepository;
	private final KnowledgeHasher hasher;
	private final Clock clock = Clock.systemUTC();

	ResumeFileService(
			ResumeFileRepository repository,
			ResumeFileParser parser,
			CandidateNormalizationService normalizationService,
			PositionPlanRepository auditRepository,
			KnowledgeHasher hasher) {
		this.repository = repository;
		this.parser = parser;
		this.normalizationService = normalizationService;
		this.auditRepository = auditRepository;
		this.hasher = hasher;
	}

	@Transactional
	CommandResult<ResumeFile> upload(
			TenantActor actor,
			UUID idempotencyKey,
			String fileName,
			String declaredMimeType,
			byte[] bytes,
			String sourceSystem,
			String externalCandidateId,
			ResumeAuditContext audit) {
		ensureTenant(actor.tenantId());
		validateFile(bytes, fileName);
		String sha256 = hasher.bytesHash(bytes);
		String normalizedSourceSystem = normalizeSourceSystem(sourceSystem);
		String normalizedExternalId = normalizeExternalCandidateId(externalCandidateId, sha256);
		String normalizedFileName = fileName.strip();
		String normalizedDeclaredMime = normalizeMime(declaredMimeType);
		String requestHash = hasher.valueHash(new UploadCommand(
			sha256, normalizedSourceSystem, normalizedExternalId, normalizedFileName, normalizedDeclaredMime));

		var previousCommand = repository.findCommand(actor.tenantId(), idempotencyKey);
		if (previousCommand.isPresent()) {
			verifyIdempotency(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}

		OffsetDateTime now = now();
		DocumentRow document = repository.lockDocumentByExternal(
			actor.tenantId(), normalizedSourceSystem, normalizedExternalId).orElse(null);
		if (document != null) {
			var duplicate = repository.findByHash(actor.tenantId(), document.id(), sha256);
			if (duplicate.isPresent()) {
				ResumeFile existing = duplicate.get();
				repository.insertCommand(
					actor.tenantId(), document.id(), idempotencyKey, requestHash, existing,
					actor.userId().toString(), now);
				return new CommandResult<>(existing, false);
			}
		}

		ParserOutcome outcome = parser.parse(bytes, normalizedFileName, normalizedDeclaredMime);
		CandidateInputReceipt candidateReceipt = outcome.parsed()
			? normalizeCandidate(
				actor, normalizedSourceSystem, normalizedExternalId, sha256, outcome, now, audit)
			: null;
		String documentStatus = outcome.parsed() ? "READY" : "PARSE_FAILED";

		if (document == null) {
			UUID documentId = UUID.randomUUID();
			document = new DocumentRow(
				documentId, normalizedSourceSystem, normalizedExternalId, documentStatus, null, 1L, now, now);
			repository.insertDocument(actor.tenantId(), document, actor.userId().toString());
		}

		int versionNo = repository.nextVersionNo(actor.tenantId(), document.id());
		UUID fileVersionId = UUID.randomUUID();
		repository.insertVersion(
			actor.tenantId(), fileVersionId, document.id(), versionNo, normalizedFileName, normalizedDeclaredMime,
			outcome.detectedMimeType(), bytes, sha256, outcome, candidateReceipt, now, actor.userId().toString());
		UUID candidateId = candidateReceipt == null ? document.candidateId() : candidateReceipt.candidate().id();
		if (repository.updateDocumentCurrent(
				actor.tenantId(), document, fileVersionId, candidateId, documentStatus, now,
				actor.userId().toString()) != 1) {
			throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Resume file was updated concurrently");
		}

		ResumeFile stored = repository.findByHash(actor.tenantId(), document.id(), sha256)
			.orElseThrow(() -> new IllegalStateException("Resume file version was not persisted"));
		repository.insertCommand(
			actor.tenantId(), document.id(), idempotencyKey, requestHash, stored, actor.userId().toString(), now);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("fileVersionId", fileVersionId);
		payload.put("sha256", sha256);
		payload.put("parseStatus", outcome.parseStatus());
		payload.put("failureCode", outcome.failureCode());
		payload.put("retryable", outcome.retryable());
		payload.put("candidateId", candidateId);
		payload.put("processingAuthorization", "TENANT_CONFIRMED_AT_UPLOAD");
		payload.put("processingAuthorizedBy", actor.userId());
		payload.put("processingAuthorizedAt", now);
		auditRepository.appendAudit(
			actor.tenantId(), user(actor), "ResumeFile", document.id(), "RESUME_FILE_UPLOADED",
			audit.requestId(), audit.traceId(), payload, now);
		return new CommandResult<>(stored, false);
	}

	@Transactional(readOnly = true)
	ResumeFile get(TenantActor actor, UUID resumeFileId) {
		ensureTenant(actor.tenantId());
		return repository.find(actor.tenantId(), resumeFileId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "RESUME_FILE_NOT_FOUND", "Resume file was not found"));
	}

	@Transactional(readOnly = true)
	PageResult<ResumeFile> list(TenantActor actor, String parseStatus, String cursor, int limit) {
		ensureTenant(actor.tenantId());
		validateLimit(limit);
		String normalizedStatus = normalizeParseStatus(parseStatus);
		int offset = decodeCursor(cursor);
		List<ResumeFile> page = repository.list(actor.tenantId(), normalizedStatus, limit + 1, offset).stream()
			.map(ResumeFile::withoutSensitiveDetail)
			.toList();
		boolean hasMore = page.size() > limit;
		List<ResumeFile> items = hasMore ? page.subList(0, limit) : page;
		return new PageResult<>(items, hasMore, hasMore ? encodeCursor(offset + limit) : null);
	}

	private CandidateInputReceipt normalizeCandidate(
			TenantActor actor,
			String sourceSystem,
			String externalCandidateId,
			String sha256,
			ParserOutcome outcome,
			OffsetDateTime now,
			ResumeAuditContext audit) {
		ParsedResumeProfile profile = outcome.parsedProfile();
		CandidateInputRequest request = new CandidateInputRequest(
			STANDALONE_CONNECTOR_ID,
			"MANUAL_IMPORT",
			sourceSystem,
			externalCandidateId,
			sha256,
			null,
			profile.name() == null ? "未识别姓名" : profile.name(),
			STANDALONE_IMPORT_CONSENT_STATUS,
			sections(outcome),
			new CandidateFacts(
				profile.location(), profile.experienceYears(), profile.educationLevel(), profile.skills()),
			null,
			now);
		UUID normalizationKey = UUID.nameUUIDFromBytes(
			("resume-normalize\n" + actor.tenantId() + "\n" + sourceSystem + "\n" + externalCandidateId + "\n" + sha256)
				.getBytes(StandardCharsets.UTF_8));
		return normalizationService.normalize(
			actor, normalizationKey, request, new AuditContext(audit.requestId(), audit.traceId())).value();
	}

	private static List<ResumeSection> sections(ParserOutcome outcome) {
		ParsedResumeProfile profile = outcome.parsedProfile();
		List<ResumeSection> sections = new ArrayList<>();
		sections.add(new ResumeSection("SUMMARY", truncate(outcome.extractedText())));
		addSection(sections, "LOCATION", profile.location());
		addSection(sections, "EDUCATION", profile.educationLevel());
		if (profile.experienceYears() != null) {
			addSection(sections, "EXPERIENCE", explicitExperienceEvidence(profile));
		}
		if (!profile.skills().isEmpty()) addSection(sections, "SKILLS", String.join(", ", profile.skills()));
		return List.copyOf(sections);
	}

	private static String explicitExperienceEvidence(ParsedResumeProfile profile) {
		return profile.evidence().stream()
			.filter(item -> "experienceYears".equals(item.field()))
			.map(ResumeFileModels.ResumeEvidence::quote)
			.findFirst()
			.orElse(profile.experienceYears().stripTrailingZeros().toPlainString() + " years of experience");
	}

	private static void addSection(List<ResumeSection> sections, String code, String text) {
		if (text != null && !text.isBlank()) sections.add(new ResumeSection(code, truncate(text)));
	}

	private static String truncate(String value) {
		return value.length() <= MAX_NORMALIZED_SECTION_LENGTH
			? value
			: value.substring(0, MAX_NORMALIZED_SECTION_LENGTH);
	}

	private static void validateFile(byte[] bytes, String fileName) {
		if (bytes == null || bytes.length == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "RESUME_FILE_EMPTY", "Resume file must not be empty");
		}
		if (bytes.length > MAX_FILE_SIZE) {
			throw new ApiException(
				HttpStatus.PAYLOAD_TOO_LARGE, "RESUME_FILE_TOO_LARGE", "Resume file exceeds the 20 MiB limit");
		}
		if (fileName == null || fileName.isBlank() || fileName.strip().length() > 255) {
			throw validation("fileName must contain between 1 and 255 characters");
		}
	}

	private static String normalizeSourceSystem(String value) {
		String normalized = value == null || value.isBlank()
			? DEFAULT_SOURCE_SYSTEM
			: value.strip().toLowerCase(Locale.ROOT);
		if (!SOURCE_SYSTEM.matcher(normalized).matches()) {
			throw validation("sourceSystem has an invalid format");
		}
		return normalized;
	}

	private static String normalizeExternalCandidateId(String value, String sha256) {
		String normalized = value == null || value.isBlank() ? "resume-" + sha256 : value.strip();
		if (normalized.length() > 256) throw validation("externalCandidateId must not exceed 256 characters");
		return normalized;
	}

	private static String normalizeMime(String mimeType) {
		if (mimeType == null || mimeType.isBlank()) return "application/octet-stream";
		String normalized = mimeType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
		if (normalized.length() > 120) throw validation("file content type must not exceed 120 characters");
		return normalized;
	}

	private static String normalizeParseStatus(String value) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.strip().toUpperCase(Locale.ROOT);
		if (!PARSE_STATUS.matcher(normalized).matches()) throw validation("parseStatus is invalid");
		return normalized;
	}

	private static void validateLimit(int limit) {
		if (limit < 1 || limit > 200) throw validation("limit must be between 1 and 200");
	}

	private static int decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) return 0;
		try {
			String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
			int offset = Integer.parseInt(value);
			if (offset < 0) throw new NumberFormatException();
			return offset;
		}
		catch (IllegalArgumentException exception) {
			throw validation("cursor is invalid");
		}
	}

	private static String encodeCursor(int offset) {
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(Integer.toString(offset).getBytes(StandardCharsets.US_ASCII));
	}

	private static void verifyIdempotency(String previousHash, String requestHash) {
		if (!secureEquals(previousHash, requestHash)) {
			throw new ApiException(
				HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
				"Idempotency key was already used with a different request");
		}
	}

	private static boolean secureEquals(String first, String second) {
		return MessageDigest.isEqual(
			first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
	}

	private void ensureTenant(UUID tenantId) {
		if (!repository.tenantExists(tenantId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant was not found or is inactive");
		}
	}

	private static ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
	}

	private static UserRef user(TenantActor actor) {
		return new UserRef(actor.userId(), actor.displayName());
	}

	private OffsetDateTime now() {
		return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	record ResumeAuditContext(UUID requestId, String traceId) {
	}

	record CommandResult<T>(T value, boolean replayed) {
	}

	private record UploadCommand(
			String sha256,
			String sourceSystem,
			String externalCandidateId,
			String fileName,
			String declaredMimeType) {
	}
}

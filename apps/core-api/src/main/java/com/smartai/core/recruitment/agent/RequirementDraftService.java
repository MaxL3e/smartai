package com.smartai.core.recruitment.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartai.core.platform.api.ApiException;
import com.smartai.core.recruitment.agent.RequirementDraftModels.CreateRequest;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ConvertRequest;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Draft;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Fields;
import com.smartai.core.recruitment.agent.RequirementDraftModels.PatchRequest;
import com.smartai.core.recruitment.agent.RequirementDraftModels.RequirementField;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Task;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

@Service
public class RequirementDraftService {

	private final RequirementDraftParser parser;
	private final RequirementDraftRepository repository;
	private final DraftConfirmationHasher confirmationHasher;
	private final Clock clock = Clock.systemUTC();

	public RequirementDraftService(
			RequirementDraftParser parser,
			RequirementDraftRepository repository,
			DraftConfirmationHasher confirmationHasher) {
		this.parser = parser;
		this.repository = repository;
		this.confirmationHasher = confirmationHasher;
	}

	@Transactional
	public CreateResult create(TenantActor actor, UUID idempotencyKey, CreateRequest request) {
		String input = request.input().strip();
		if (input.length() < 10) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "input must contain at least 10 characters");
		}
		ensureTenantExists(actor.tenantId());
		String requestHash = requestHash(request, input);
		var previous = repository.findByIdempotencyKey(actor.tenantId(), idempotencyKey);
		if (previous.isPresent()) {
			if (!MessageDigest.isEqual(
					previous.get().requestHash().getBytes(StandardCharsets.US_ASCII),
					requestHash.getBytes(StandardCharsets.US_ASCII))) {
				throw new ApiException(
					HttpStatus.CONFLICT,
					"IDEMPOTENCY_CONFLICT",
					"Idempotency key was already used with a different request");
			}
			return new CreateResult(previous.get().draft(), true);
		}
		OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		Fields fields = parser.parse(input);
		Draft draft = new Draft(
			UUID.randomUUID(),
			isReady(fields) ? "READY" : "DRAFT",
			1L,
			input,
			fields,
			request.sourceJobRef(),
			request.hostContextHash(),
			new UserRef(actor.userId(), actor.displayName()),
			now,
			now,
			now.plusHours(24),
			null);
		repository.insert(actor.tenantId(), idempotencyKey, requestHash, draft);
		return new CreateResult(draft, false);
	}

	@Transactional(readOnly = true)
	public Draft get(TenantActor actor, UUID draftId) {
		Draft draft = repository.find(actor.tenantId(), draftId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND,
				"RESOURCE_NOT_FOUND",
				"Requirement draft was not found"));
		return expiredView(draft);
	}

	@Transactional
	public CommandResult<Draft> patch(
			TenantActor actor,
			UUID draftId,
			long expectedVersion,
			UUID idempotencyKey,
			PatchRequest request) {
		if (request.rawInput() == null && request.fields() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Patch must contain rawInput or fields");
		}
		Draft current = get(actor, draftId);
		String requestHash = confirmationHasher.commandHash("PATCH", draftId, expectedVersion, request);
		var previousCommand = repository.findCommand(actor.tenantId(), "PATCH", idempotencyKey, Draft.class);
		if (previousCommand.isPresent()) {
			verifyIdempotencyHash(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}
		ensureNotExpired(current);
		ensureMutable(current);
		VersionPrecondition.verify(expectedVersion, current.version());

		String rawInput = current.rawInput();
		Fields fields = current.fields();
		if (request.rawInput() != null) {
			rawInput = request.rawInput().strip();
			if (rawInput.length() < 10) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "rawInput must contain at least 10 characters");
			}
			fields = parser.parse(rawInput);
		}
		fields = mergeFields(fields, request.fields());
		OffsetDateTime now = now();
		Draft updated = new Draft(
			current.id(),
			isReady(fields) ? "READY" : "DRAFT",
			current.version() + 1,
			rawInput,
			fields,
			current.sourceJobRef(),
			current.hostContextHash(),
			current.createdBy(),
			current.createdAt(),
			now,
			current.expiresAt(),
			current.convertedTaskRef());
		if (repository.update(actor.tenantId(), current, updated, actor.userId().toString()) != 1) {
			var concurrentReplay = repository.findCommand(actor.tenantId(), "PATCH", idempotencyKey, Draft.class);
			if (concurrentReplay.isPresent()) {
				verifyIdempotencyHash(concurrentReplay.get().requestHash(), requestHash);
				return new CommandResult<>(concurrentReplay.get().response(), true);
			}
			throw versionConflict();
		}
		repository.insertCommand(
			actor.tenantId(), draftId, "PATCH", idempotencyKey, requestHash, updated,
			updated.version(), actor.userId().toString());
		return new CommandResult<>(updated, false);
	}

	@Transactional
	public CommandResult<Task> convert(
			TenantActor actor,
			UUID draftId,
			long expectedVersion,
			UUID idempotencyKey,
			ConvertRequest request) {
		Draft current = get(actor, draftId);
		String requestHash = confirmationHasher.commandHash("CONVERT", draftId, expectedVersion, request);
		var previousCommand = repository.findCommand(actor.tenantId(), "CONVERT", idempotencyKey, Task.class);
		if (previousCommand.isPresent()) {
			verifyIdempotencyHash(previousCommand.get().requestHash(), requestHash);
			return new CommandResult<>(previousCommand.get().response(), true);
		}
		ensureNotExpired(current);
		if ("CONVERTED".equals(current.status())) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"DRAFT_ALREADY_CONVERTED",
				"Requirement draft has already been converted");
		}
		ensureMutable(current);
		VersionPrecondition.verify(expectedVersion, current.version());
		String currentInputHash = confirmationHasher.confirmationHash(current);
		if (!MessageDigest.isEqual(
				currentInputHash.getBytes(StandardCharsets.US_ASCII),
				request.confirmation().inputHash().getBytes(StandardCharsets.US_ASCII))) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"CONFIRMATION_INPUT_CHANGED",
				"Confirmed input does not match the current requirement draft");
		}
		List<UUID> participantIds = request.participantUserIds() == null ? List.of() : request.participantUserIds();
		if (new HashSet<>(participantIds).size() != participantIds.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "participantUserIds must be unique");
		}

		Task task = toTask(actor, current, request);
		Draft conversionClaim = new Draft(
			current.id(), "CONVERTED", current.version() + 1, current.rawInput(), current.fields(),
			current.sourceJobRef(), current.hostContextHash(), current.createdBy(), current.createdAt(),
			task.createdAt(), current.expiresAt(), null);
		if (repository.update(actor.tenantId(), current, conversionClaim, actor.userId().toString()) != 1) {
			var concurrentReplay = repository.findCommand(actor.tenantId(), "CONVERT", idempotencyKey, Task.class);
			if (concurrentReplay.isPresent()) {
				verifyIdempotencyHash(concurrentReplay.get().requestHash(), requestHash);
				return new CommandResult<>(concurrentReplay.get().response(), true);
			}
			Draft latest = get(actor, draftId);
			if ("CONVERTED".equals(latest.status())) {
				throw new ApiException(
					HttpStatus.CONFLICT,
					"DRAFT_ALREADY_CONVERTED",
					"Requirement draft has already been converted");
			}
			throw versionConflict();
		}
		repository.insertTask(actor.tenantId(), draftId, task, actor.userId().toString());
		repository.insertCreationCheckpoint(
			actor.tenantId(), draftId, task.creationCheckpointRef().id(), task, currentInputHash,
			request.confirmation().comment(), new UserRef(actor.userId(), actor.displayName()));
		if (repository.linkConvertedTask(
				actor.tenantId(), draftId, conversionClaim.version(), task.id(), task.createdAt(),
				actor.userId().toString()) != 1) {
			throw new IllegalStateException("Unable to link converted recruitment task");
		}
		repository.insertCommand(
			actor.tenantId(), draftId, "CONVERT", idempotencyKey, requestHash, task,
			task.version(), actor.userId().toString());
		return new CommandResult<>(task, false);
	}

	@Transactional(readOnly = true)
	public Task getTask(TenantActor actor, UUID taskId) {
		return repository.findTask(actor.tenantId(), taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Recruitment task was not found"));
	}

	String confirmationHash(Draft draft) {
		return confirmationHasher.confirmationHash(draft);
	}

	private void ensureTenantExists(UUID tenantId) {
		if (!repository.tenantExists(tenantId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant was not found or is inactive");
		}
	}

	private Task toTask(TenantActor actor, Draft draft, ConvertRequest request) {
		String positionName = requiredString(draft.fields().positionName(), "positionName");
		String organization = requiredString(draft.fields().organizationRef(), "organizationRef");
		List<String> locations = requiredStrings(draft.fields().locations(), "locations");
		int headcount = requiredHeadcount(draft.fields().headcount());
		String recruitmentType = requiredEnum(
			draft.fields().recruitmentType(), "recruitmentType", Set.of("SOCIAL", "CAMPUS", "INTERNAL"));
		String priority = requiredEnum(
			draft.fields().priority(), "priority", Set.of("LOW", "NORMAL", "HIGH", "URGENT"));
		LocalDate targetDate = optionalDate(draft.fields().targetDate());
		OffsetDateTime now = now();
		UUID taskId = UUID.randomUUID();
		UUID checkpointId = UUID.randomUUID();
		List<UserRef> participants = (request.participantUserIds() == null ? List.<UUID>of() : request.participantUserIds())
			.stream().map(id -> userRef(actor, id)).toList();
		return new Task(
			taskId,
			"RT-" + now.toLocalDate().toString().replace("-", "") + "-" + taskId.toString().substring(0, 8).toUpperCase(),
			(positionName + "招聘任务").substring(0, Math.min(positionName.length() + 4, 200)),
			positionName,
			new ResourceRef("Organization", UUID.nameUUIDFromBytes(
				(actor.tenantId() + "|organization|" + organization).getBytes(StandardCharsets.UTF_8)), 1L),
			userRef(actor, request.ownerUserId()),
			request.hiringManagerUserId() == null ? null : userRef(actor, request.hiringManagerUserId()),
			participants,
			recruitmentType,
			headcount,
			locations,
			priority,
			targetDate,
			"ROLE_PLAN",
			"ACTIVE",
			"IDLE",
			1L,
			new ResourceRef("HumanCheckpoint", checkpointId, 1L),
			null,
			draft.sourceJobRef(),
			now,
			now);
	}

	private static UserRef userRef(TenantActor actor, UUID userId) {
		return new UserRef(
			userId,
			userId.equals(actor.userId()) ? actor.displayName() : "用户 " + userId.toString().substring(0, 8));
	}

	private static String requiredString(RequirementField field, String name) {
		if (field == null || !(field.value() instanceof String value) || value.isBlank()) throw incomplete(name);
		return value.strip();
	}

	private static int requiredHeadcount(RequirementField field) {
		if (field == null || !(field.value() instanceof Number number)) throw incomplete("headcount");
		int value = number.intValue();
		if (value < 1 || value > 10000) throw incomplete("headcount");
		return value;
	}

	private static List<String> requiredStrings(RequirementField field, String name) {
		if (field == null || !(field.value() instanceof Collection<?> values)) throw incomplete(name);
		List<String> result = values.stream()
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.map(String::strip)
			.filter(value -> !value.isBlank())
			.distinct()
			.toList();
		if (result.isEmpty()) throw incomplete(name);
		return result;
	}

	private static String requiredEnum(RequirementField field, String name, Set<String> allowed) {
		String value = requiredString(field, name);
		if (!allowed.contains(value)) throw incomplete(name);
		return value;
	}

	private static LocalDate optionalDate(RequirementField field) {
		if (field == null || field.value() == null) return null;
		if (!(field.value() instanceof String value) || value.isBlank()) return null;
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException exception) {
			throw incomplete("targetDate must use ISO yyyy-MM-dd format");
		}
	}

	private static Fields mergeFields(Fields current, Fields patch) {
		if (patch == null) return current;
		return new Fields(
			choose(current.positionName(), patch.positionName()),
			choose(current.organizationRef(), patch.organizationRef()),
			choose(current.locations(), patch.locations()),
			choose(current.headcount(), patch.headcount()),
			choose(current.recruitmentType(), patch.recruitmentType()),
			choose(current.priority(), patch.priority()),
			choose(current.targetDate(), patch.targetDate()),
			choose(current.coreRequirements(), patch.coreRequirements()),
			choose(current.knowledgeScope(), patch.knowledgeScope()));
	}

	private static RequirementField choose(RequirementField current, RequirementField patch) {
		return patch == null ? current : patch;
	}

	private static boolean isReady(Fields fields) {
		return hasValue(fields.positionName())
			&& hasValue(fields.organizationRef())
			&& hasValue(fields.locations())
			&& hasValue(fields.headcount());
	}

	private static boolean hasValue(RequirementField field) {
		if (field == null || field.value() == null) return false;
		if (field.value() instanceof String value) return !value.isBlank();
		if (field.value() instanceof Collection<?> values) return !values.isEmpty();
		return true;
	}

	private static void ensureMutable(Draft draft) {
		if (!Set.of("DRAFT", "READY").contains(draft.status())) {
			throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_STATE_CONFLICT", "Requirement draft is not editable");
		}
	}

	private static void ensureNotExpired(Draft draft) {
		if ("EXPIRED".equals(draft.status()) || draft.expiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
			throw new ApiException(HttpStatus.GONE, "DRAFT_EXPIRED", "Requirement draft has expired");
		}
	}

	private static Draft expiredView(Draft draft) {
		if (!Set.of("DRAFT", "READY").contains(draft.status())
				|| !draft.expiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
			return draft;
		}
		return new Draft(
			draft.id(), "EXPIRED", draft.version(), draft.rawInput(), draft.fields(), draft.sourceJobRef(),
			draft.hostContextHash(), draft.createdBy(), draft.createdAt(), draft.updatedAt(), draft.expiresAt(),
			draft.convertedTaskRef());
	}

	private static ApiException incomplete(String field) {
		return new ApiException(
			HttpStatus.BAD_REQUEST,
			"DRAFT_INCOMPLETE",
			"Requirement draft field is missing or invalid: " + field);
	}

	private static ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Resource was updated concurrently");
	}

	private static void verifyIdempotencyHash(String previousHash, String requestHash) {
		if (!MessageDigest.isEqual(
				previousHash.getBytes(StandardCharsets.US_ASCII),
				requestHash.getBytes(StandardCharsets.US_ASCII))) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"IDEMPOTENCY_CONFLICT",
				"Idempotency key was already used with a different request");
		}
	}

	private OffsetDateTime now() {
		return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	private static String requestHash(CreateRequest request, String normalizedInput) {
		String canonical = normalizedInput + "\u001f"
			+ String.valueOf(request.sourceJobRef()) + "\u001f"
			+ String.valueOf(request.hostContextHash()) + "\u001f"
			+ String.valueOf(request.locale() == null ? "zh-CN" : request.locale());
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	record CreateResult(Draft draft, boolean replayed) {
	}

	record CommandResult<T>(T value, boolean replayed) {
	}
}

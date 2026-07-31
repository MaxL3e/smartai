package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class RequirementDraftModels {

	private RequirementDraftModels() {
	}

	record CreateRequest(
			@NotBlank @Size(min = 10, max = 10000) String input,
			@Valid ExternalRef sourceJobRef,
			@Pattern(regexp = "^[a-f0-9]{64}$") String hostContextHash,
			@Pattern(regexp = "^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$") String locale) {
	}

	record ExternalRef(
			@NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{1,63}$") String system,
			@NotBlank @Size(max = 256) String id,
			@Size(max = 80) String objectType,
			Object version) {
	}

	record UserRef(UUID id, String displayName) {
	}

	record RequirementField(
			Object value,
			@NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
			@NotNull @Pattern(regexp = "^(USER|DEFAULT|AI|IMPORTED)$") String source,
			@NotNull Boolean needsConfirmation,
			@Size(max = 1000)
			String evidence) {
	}

	record Fields(
			@Valid RequirementField positionName,
			@Valid RequirementField organizationRef,
			@Valid RequirementField locations,
			@Valid RequirementField headcount,
			@Valid RequirementField recruitmentType,
			@Valid RequirementField priority,
			@Valid RequirementField targetDate,
			@Valid RequirementField coreRequirements,
			@Valid RequirementField knowledgeScope) {
	}

	record PatchRequest(
			@Size(min = 10, max = 10000) String rawInput,
			@Valid Fields fields) {
	}

	record HumanConfirmation(
			@NotNull @AssertTrue Boolean confirmed,
			@NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String inputHash,
			@Size(max = 2000) String comment) {
	}

	record ConvertRequest(
			@NotNull @Valid HumanConfirmation confirmation,
			@NotNull UUID ownerUserId,
			UUID hiringManagerUserId,
			@Size(max = 100) List<UUID> participantUserIds) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record Draft(
			UUID id,
			String status,
			long version,
			String rawInput,
			Fields fields,
			ExternalRef sourceJobRef,
			String hostContextHash,
			UserRef createdBy,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt,
			OffsetDateTime expiresAt,
			ResourceRef convertedTaskRef) {
	}

	record ResourceRef(String type, UUID id, long version) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record Task(
			UUID id,
			String taskNo,
			String title,
			String positionName,
			ResourceRef organizationRef,
			UserRef owner,
			UserRef hiringManager,
			List<UserRef> participants,
			String recruitmentType,
			int headcount,
			List<String> locations,
			String priority,
			LocalDate targetDate,
			String businessStage,
			String lifecycleStatus,
			String executionStatus,
			long version,
			ResourceRef creationCheckpointRef,
			ResourceRef currentPlanVersionRef,
			ExternalRef sourceJobRef,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	record TenantActor(UUID tenantId, UUID userId, String displayName) {
	}
}

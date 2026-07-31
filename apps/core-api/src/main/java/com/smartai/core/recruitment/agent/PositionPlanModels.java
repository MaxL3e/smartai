package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

final class PositionPlanModels {

	private PositionPlanModels() {
	}

	record GenerateRequest(
			@NotNull @Valid ResourceRef requirementDraftRef,
			@NotNull @Size(max = 100) List<@Valid ResourceRef> knowledgeVersionRefs,
			@Size(max = 4000) String instructions) {
	}

	record HardConstraint(
			@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String code,
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Size(max = 120) String field,
			@NotBlank @Pattern(regexp = "^(EQ|IN|GTE|LTE|EXISTS)$") String operator,
			@NotNull Object value,
			@NotBlank @Size(max = 500) String reason,
			@Size(max = 100) List<@Valid ResourceRef> sourceRefs) {
	}

	record ScoringRule(
			@NotBlank @Pattern(regexp = "^(RANGE_TABLE|BOOLEAN|PRESENCE|CATEGORY_MAP)$") String type,
			@NotNull Map<String, Object> parameters,
			@Size(max = 80) String calculationVersion) {
	}

	record ScoreCriterion(
			@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String code,
			@NotBlank @Size(max = 120) String name,
			@NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal weight,
			@NotBlank @Size(max = 1000) String description,
			@NotBlank @Size(max = 1000) String evidenceRequirement,
			@NotNull @Valid ScoringRule scoringRule,
			@NotNull Boolean required,
			@DecimalMin("0") @DecimalMax("100") BigDecimal capScore,
			@NotNull @PositiveOrZero Integer displayOrder) {
	}

	record RecommendationThreshold(
			@NotBlank @Pattern(regexp = "^(NOT_RECOMMENDED|REVIEW|RECOMMENDED|STRONGLY_RECOMMENDED)$") String level,
			@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minimum,
			@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal maximum) {
	}

	record ScorecardVersion(
			@NotNull UUID id,
			@Positive int versionNo,
			@NotNull @DecimalMin("100") @DecimalMax("100") BigDecimal totalScore,
			@NotEmpty @Size(max = 50) List<@Valid ScoreCriterion> criteria,
			@NotEmpty @Size(max = 10) List<@Valid RecommendationThreshold> thresholds,
			@NotBlank @Pattern(regexp = "^(ZERO_AND_FLAG|NO_SCORE_AND_REVIEW|REQUIRE_HUMAN)$") String missingEvidencePolicy,
			@NotBlank @Pattern(regexp = "^EXCLUDE_FROM_SCORING$") String sensitiveFeaturePolicy,
			@NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String contentHash) {
	}

	record PatchRequest(
			@Size(min = 1, max = 20000) String jobDescription,
			@Size(min = 1, max = 100) List<@NotBlank @Size(max = 1000) String> responsibilities,
			@Size(min = 1, max = 100) List<@NotBlank @Size(max = 1000) String> requirements,
			@Size(max = 50) List<@Valid HardConstraint> hardConstraints,
			@Valid ScorecardVersion scorecard,
			@Size(min = 1, max = 2000) String changeSummary) {
	}

	record HumanReviewRequest(
			@NotBlank @Pattern(regexp = "^(RECRUITMENT_MANAGER|HIRING_MANAGER|KNOWLEDGE_ADMIN)$") String requiredRole,
			UUID assigneeUserId,
			@NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String inputHash,
			@Size(max = 2000) String comment,
			OffsetDateTime expiresAt) {
	}

	record DecisionRequest(
			@NotBlank @Pattern(regexp = "^(APPROVE|REJECT|CANCEL)$") String decision,
			@NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String inputHash,
			@Size(max = 2000) String comment) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record PositionPlanVersion(
			UUID id,
			UUID taskId,
			int versionNo,
			String status,
			long version,
			String jobDescription,
			List<String> responsibilities,
			List<String> requirements,
			List<HardConstraint> hardConstraints,
			ScorecardVersion scorecard,
			String generatedBy,
			UUID basedOnRunId,
			List<ResourceRef> knowledgeVersionRefs,
			String promptVersion,
			String contentHash,
			String changeSummary,
			ResourceRef approvalCheckpointRef,
			UserRef approvedBy,
			OffsetDateTime approvedAt,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record AgentRun(
			UUID id,
			UUID taskId,
			String runType,
			String status,
			String workflowVersion,
			String traceId,
			String failureCode,
			OffsetDateTime createdAt,
			OffsetDateTime startedAt,
			OffsetDateTime finishedAt) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record HumanCheckpoint(
			UUID id,
			UUID taskId,
			String type,
			ResourceRef resourceRef,
			String status,
			String requiredRole,
			UUID assigneeUserId,
			String inputHash,
			long version,
			String summary,
			UserRef requestedBy,
			OffsetDateTime requestedAt,
			OffsetDateTime expiresAt,
			String decision,
			String comment,
			UserRef decidedBy,
			OffsetDateTime decidedAt) {
	}
}

package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ExternalRef;
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
import jakarta.validation.constraints.Size;

final class MatchingModels {

	private MatchingModels() {
	}

	record CandidateInputRequest(
			@NotNull UUID connectorId,
			@NotBlank @Pattern(regexp = "^(ATS_APPLICATION|TALENT_POOL|MANUAL_IMPORT)$") String sourceType,
			@NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{1,63}$") String sourceSystem,
			@NotBlank @Size(max = 256) String externalCandidateId,
			@NotBlank @Size(max = 128) String sourceVersion,
			@Size(max = 60) String candidateNo,
			@NotBlank @Size(max = 120) String displayName,
			@NotBlank @Pattern(regexp = "^(UNKNOWN|GRANTED|REVOKED|EXPIRED)$") String consentStatus,
			@NotEmpty @Size(max = 50) List<@Valid ResumeSection> sections,
			@NotNull @Valid CandidateFacts facts,
			@Valid ExternalRef sourceApplicationRef,
			@NotNull OffsetDateTime sourceUpdatedAt) {
	}

	record ResumeSection(
			@NotBlank @Pattern(regexp = "^(SUMMARY|EXPERIENCE|SKILLS|EDUCATION|LOCATION|CERTIFICATION|PROJECT)$") String code,
			@NotBlank @Size(max = 20000) String text) {
	}

	record CandidateFacts(
			@Size(max = 100) String location,
			@DecimalMin("0") @DecimalMax("80") BigDecimal experienceYears,
			@Size(max = 80) String educationLevel,
			@Size(max = 100) List<@NotBlank @Size(max = 100) String> skills) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record CandidateSummary(
			UUID id,
			String candidateNo,
			String displayName,
			String consentStatus,
			ExternalRef sourceRef) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record CandidateInputReceipt(
			CandidateSummary candidate,
			ResourceRef resumeVersionRef,
			UUID connectorId,
			String sourceType,
			String contentHash,
			String normalizerKind,
			OffsetDateTime normalizedAt) {
	}

	record CandidateFilters(
			@Size(max = 50) List<@NotBlank @Size(max = 100) String> keywords,
			@Size(max = 50) List<@NotBlank @Size(max = 100) String> locations,
			@DecimalMin("0") @DecimalMax("80") BigDecimal minimumExperienceYears,
			@Size(max = 20) List<@NotBlank @Size(max = 80) String> educationLevels,
			OffsetDateTime updatedAfter) {
	}

	record CandidateScope(
			@NotEmpty @Size(max = 100) List<@NotNull UUID> connectorIds,
			@NotNull @Valid CandidateFilters filters,
			@NotNull OffsetDateTime dataCutoffAt,
			@Positive Integer maximumCandidates) {
	}

	record MatchRunCreateRequest(
			@NotNull @Valid ResourceRef positionPlanVersionRef,
			@NotNull @Valid ResourceRef scorecardVersionRef,
			@NotNull @Valid CandidateScope candidateScope,
			@DecimalMin("0") @DecimalMax("100") BigDecimal minimumRecommendationScore) {
	}

	record MatchRunMetrics(
			int scanned,
			int hardFiltered,
			int recalled,
			int reranked,
			int scored,
			int failed,
			Long durationMs) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record MatchRun(
			UUID id,
			UUID taskId,
			ResourceRef positionPlanVersionRef,
			ResourceRef scorecardVersionRef,
			CandidateScope candidateScope,
			String searchIndexVersion,
			String pipelineVersion,
			String generatorKind,
			String status,
			long version,
			MatchRunMetrics metrics,
			UserRef requestedBy,
			OffsetDateTime createdAt,
			OffsetDateTime startedAt,
			OffsetDateTime finishedAt,
			String failureCode) {
	}

	record EvidenceSourceLocator(
			Integer page,
			String section,
			Integer paragraph,
			Integer startOffset,
			Integer endOffset,
			Long timeStartMs,
			Long timeEndMs) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record EvidenceRef(
			UUID id,
			String sourceType,
			ResourceRef sourceVersionRef,
			EvidenceSourceLocator sourceLocator,
			String quote,
			String quoteHash,
			BigDecimal extractionConfidence,
			UUID modelInvocationId,
			UserRef verifiedBy,
			OffsetDateTime verifiedAt) {
	}

	record HardFilterItem(
			String constraintCode,
			boolean passed,
			String reason,
			List<EvidenceRef> evidenceRefs) {
	}

	record HardFilterResult(boolean passed, List<HardFilterItem> items) {
	}

	record CriterionScore(
			String criterionCode,
			BigDecimal rawScore,
			BigDecimal weightedScore,
			String calculationVersion,
			String evidenceStatus,
			List<EvidenceRef> evidenceRefs,
			String explanation) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record MatchResult(
			UUID id,
			UUID matchRunId,
			ResourceRef taskCandidateRef,
			CandidateSummary candidate,
			ResourceRef resumeVersionRef,
			ResourceRef scorecardVersionRef,
			int rank,
			BigDecimal totalScore,
			String recommendationLevel,
			String generatorKind,
			HardFilterResult hardFilterResult,
			List<CriterionScore> criterionScores,
			BigDecimal confidence,
			String reviewStatus,
			long version,
			List<String> needsVerification) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record TaskCandidate(
			UUID id,
			UUID taskId,
			CandidateSummary candidate,
			String status,
			String selectionStatus,
			long version,
			ResourceRef currentMatchResultRef,
			ResourceRef candidateListVersionRef,
			String sourceType,
			ExternalRef sourceApplicationRef,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	record NormalizedCandidate(
			CandidateSummary candidate,
			UUID connectorId,
			String sourceType,
			ExternalRef sourceApplicationRef,
			ResourceRef resumeVersionRef,
			List<ResumeSection> sections,
			CandidateFacts facts,
			String contentHash,
			OffsetDateTime sourceUpdatedAt) {
	}

	record PageResult<T>(List<T> items, boolean hasMore, String nextCursor) {
	}
}

package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateSummary;
import com.smartai.core.recruitment.agent.MatchingModels.EvidenceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.UserRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class CandidateListModels {

	private CandidateListModels() {
	}

	record CandidateInvitationPlan(
			@NotNull UUID connectorId,
			@NotBlank @Size(max = 200) String templateId,
			@NotNull OffsetDateTime deadline,
			@NotBlank @Pattern(regexp = "^(SMS|EMAIL|ENTERPRISE_MESSAGE)$") String channel,
			@NotBlank @Size(max = 200) String messageTemplateId,
			@NotBlank @Size(max = 2000) String externalImpactSummary) {
	}

	record CandidateListPreviewRequest(
			@NotNull @Valid ResourceRef matchRunRef,
			@NotEmpty @Size(max = 500) List<@NotNull @Valid ResourceRef> taskCandidateRefs,
			@Size(max = 500) Map<@NotNull UUID, @Size(max = 2000) String> selectionNotes,
			@NotNull @Valid CandidateInvitationPlan invitationPlan) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record CandidateListPreviewItem(
			ResourceRef taskCandidateRef,
			ResourceRef matchResultRef,
			CandidateSummary candidate,
			String selectionReason,
			String note,
			List<EvidenceRef> evidenceRefs,
			List<String> needsVerification) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record CandidateListPreview(
			UUID id,
			UUID taskId,
			long version,
			ResourceRef matchRunRef,
			List<CandidateListPreviewItem> items,
			CandidateInvitationPlan invitationPlan,
			String inputHash,
			OffsetDateTime expiresAt) {
	}

	record CandidateListReviewRequest(
			@NotNull @Valid ResourceRef previewRef,
			@NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String inputHash,
			@NotBlank @Pattern(regexp = "^(RECRUITMENT_MANAGER|HIRING_MANAGER)$") String requiredRole,
			UUID assigneeUserId,
			@Size(max = 2000) String comment,
			OffsetDateTime expiresAt) {
	}

	record CandidateListConfirmRequest(
			@NotNull @Valid ResourceRef previewRef,
			@NotNull UUID checkpointId) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record CandidateListVersion(
			UUID id,
			UUID taskId,
			int versionNo,
			ResourceRef previewRef,
			ResourceRef matchRunRef,
			List<ResourceRef> taskCandidateRefs,
			CandidateInvitationPlan invitationPlan,
			ResourceRef checkpointRef,
			UserRef confirmedBy,
			OffsetDateTime confirmedAt,
			String contentHash) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record RecommendationCriterion(
			String criterionCode,
			BigDecimal weightedScore,
			String evidenceStatus,
			List<EvidenceRef> sourceEvidenceRefs,
			String systemJudgment) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record RecommendationCandidate(
			ResourceRef taskCandidateRef,
			ResourceRef matchResultRef,
			CandidateSummary candidate,
			ResourceRef resumeVersionRef,
			int rank,
			BigDecimal totalScore,
			String recommendationLevel,
			boolean hardFilterPassed,
			String selectionReason,
			String note,
			List<RecommendationCriterion> criteria,
			List<String> needsVerification) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record RecommendationReport(
			UUID id,
			UUID taskId,
			int versionNo,
			ResourceRef candidateListVersionRef,
			ResourceRef positionPlanVersionRef,
			ResourceRef scorecardVersionRef,
			ResourceRef matchRunRef,
			List<RecommendationCandidate> candidates,
			UserRef generatedBy,
			OffsetDateTime generatedAt,
			String contentHash) {
	}
}

package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateInputReceipt;
import com.smartai.core.recruitment.agent.MatchingModels.CandidateSummary;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;

final class ResumeFileModels {

	private ResumeFileModels() {
	}

	record ResumeEvidence(
			String field,
			String section,
			String quote,
			int startOffset,
			int endOffset) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record ParsedResumeProfile(
			String name,
			List<String> emails,
			List<String> phones,
			String educationLevel,
			BigDecimal experienceYears,
			List<String> skills,
			String location,
			List<ResumeEvidence> evidence) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record ResumeFile(
			UUID id,
			int fileVersion,
			String originalFileName,
			String mimeType,
			long sizeBytes,
			String sha256,
			String parseStatus,
			String failureCode,
			String parserVersion,
			boolean retryable,
			String extractedText,
			List<ResumeEvidence> evidence,
			ParsedResumeProfile parsedProfile,
			CandidateSummary candidate,
			ResourceRef resumeVersionRef,
			CandidateInputReceipt candidateReceipt,
			String sourceSystem,
			String externalCandidateId,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {

		ResumeFile withoutSensitiveDetail() {
			ParsedResumeProfile summaryProfile = parsedProfile == null ? null : new ParsedResumeProfile(
				parsedProfile.name(), List.of(), List.of(), parsedProfile.educationLevel(),
				parsedProfile.experienceYears(), parsedProfile.skills(), parsedProfile.location(), List.of());
			return new ResumeFile(
				id, fileVersion, originalFileName, mimeType, sizeBytes, sha256, parseStatus, failureCode,
				parserVersion, retryable, null, List.of(), summaryProfile, candidate, resumeVersionRef,
				null, sourceSystem, externalCandidateId, createdAt, updatedAt);
		}
	}

	record ParserOutcome(
			String detectedMimeType,
			String extractedText,
			String parseStatus,
			String parserVersion,
			String failureCode,
			boolean retryable,
			ParsedResumeProfile parsedProfile) {

		boolean parsed() {
			return "PARSED".equals(parseStatus);
		}
	}

	record StoredFileVersion(ResumeFile resumeFile) {
	}

	record PageResult<T>(List<T> items, boolean hasMore, String nextCursor) {
	}
}

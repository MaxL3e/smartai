package com.smartai.core.recruitment.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartai.core.recruitment.agent.PositionPlanModels.PositionPlanVersion;
import com.smartai.core.recruitment.agent.PositionPlanModels.ScorecardVersion;

@Component
final class PositionPlanHasher {

	private final ObjectMapper objectMapper;

	PositionPlanHasher(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	String planContentHash(PositionPlanVersion plan) {
		return sha256(json(new PlanContent(
			plan.jobDescription(),
			plan.responsibilities(),
			plan.requirements(),
			plan.hardConstraints(),
			plan.scorecard(),
			plan.generatedBy(),
			plan.knowledgeVersionRefs(),
			plan.promptVersion())));
	}

	String scorecardContentHash(ScorecardVersion scorecard) {
		return sha256(json(new ScorecardContent(
			scorecard.totalScore(),
			scorecard.criteria(),
			scorecard.thresholds(),
			scorecard.missingEvidencePolicy(),
			scorecard.sensitiveFeaturePolicy())));
	}

	String commandHash(String operation, UUID resourceId, Long expectedVersion, Object body) {
		return sha256(json(new CommandContent(operation, resourceId, expectedVersion, body)));
	}

	String sha256Value(Object value) {
		return sha256(json(value));
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize position plan hash input", exception);
		}
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private record PlanContent(
			Object jobDescription,
			Object responsibilities,
			Object requirements,
			Object hardConstraints,
			Object scorecard,
			String generatedBy,
			Object knowledgeVersionRefs,
			String promptVersion) {
	}

	private record ScorecardContent(
			Object totalScore,
			Object criteria,
			Object thresholds,
			String missingEvidencePolicy,
			String sensitiveFeaturePolicy) {
	}

	private record CommandContent(String operation, UUID resourceId, Long expectedVersion, Object body) {
	}
}

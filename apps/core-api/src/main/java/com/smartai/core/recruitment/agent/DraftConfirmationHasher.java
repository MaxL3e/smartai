package com.smartai.core.recruitment.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Draft;

@Component
final class DraftConfirmationHasher {

	private final ObjectMapper objectMapper;

	DraftConfirmationHasher(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	String confirmationHash(Draft draft) {
		return sha256(json(new ConfirmationSnapshot(
			draft.id(),
			draft.version(),
			draft.rawInput(),
			draft.fields(),
			draft.sourceJobRef(),
			draft.hostContextHash())));
	}

	String commandHash(String operation, UUID draftId, long expectedVersion, Object body) {
		return sha256(json(new CommandSnapshot(operation, draftId, expectedVersion, body)));
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to hash requirement draft snapshot", exception);
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

	private record ConfirmationSnapshot(
			UUID id,
			long version,
			String rawInput,
			Object fields,
			Object sourceJobRef,
			String hostContextHash) {
	}

	private record CommandSnapshot(String operation, UUID draftId, long expectedVersion, Object body) {
	}
}

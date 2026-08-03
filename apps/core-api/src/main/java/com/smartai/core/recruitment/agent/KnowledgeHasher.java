package com.smartai.core.recruitment.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
final class KnowledgeHasher {

	private final ObjectMapper objectMapper;

	KnowledgeHasher(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	String commandHash(String operation, UUID resourceId, Long expectedVersion, Object body) {
		return sha256(json(new CommandContent(operation, resourceId, expectedVersion, body))
			.getBytes(StandardCharsets.UTF_8));
	}

	String valueHash(Object value) {
		return sha256(json(value).getBytes(StandardCharsets.UTF_8));
	}

	String textHash(String value) {
		return sha256(value.getBytes(StandardCharsets.UTF_8));
	}

	String bytesHash(byte[] value) {
		return sha256(value);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize knowledge hash input", exception);
		}
	}

	private static String sha256(byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private record CommandContent(String operation, UUID resourceId, Long expectedVersion, Object body) {
	}
}

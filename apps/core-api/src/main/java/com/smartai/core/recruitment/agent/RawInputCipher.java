package com.smartai.core.recruitment.agent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class RawInputCipher {

	private static final int NONCE_BYTES = 12;
	private static final int GCM_TAG_BITS = 128;
	private static final byte FORMAT_VERSION = 1;

	private final SecretKeySpec key;
	private final SecureRandom secureRandom = new SecureRandom();

	RawInputCipher(@Value("${smartai.requirement-drafts.encryption-key}") String encodedKey) {
		byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
		if (keyBytes.length != 32) throw new IllegalArgumentException("Requirement draft encryption key must be 256-bit");
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	String encrypt(String plainText) {
		byte[] nonce = new byte[NONCE_BYTES];
		secureRandom.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
			byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(1 + nonce.length + cipherText.length)
				.put(FORMAT_VERSION)
				.put(nonce)
				.put(cipherText)
				.array());
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Unable to encrypt requirement draft input", exception);
		}
	}

	String decrypt(String encoded) {
		byte[] payload = Base64.getDecoder().decode(encoded);
		ByteBuffer buffer = ByteBuffer.wrap(payload);
		if (buffer.get() != FORMAT_VERSION || buffer.remaining() <= NONCE_BYTES) {
			throw new IllegalStateException("Unsupported requirement draft ciphertext");
		}
		byte[] nonce = new byte[NONCE_BYTES];
		buffer.get(nonce);
		byte[] cipherText = new byte[buffer.remaining()];
		buffer.get(cipherText);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
			return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Unable to decrypt requirement draft input", exception);
		}
	}
}

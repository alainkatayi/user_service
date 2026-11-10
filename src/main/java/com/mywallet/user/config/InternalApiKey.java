package com.mywallet.user.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mywallet.user.exception.ForbiddenException;

@Component
public class InternalApiKey {

	private final byte[] expected;

	public InternalApiKey(@Value("${app.internal.api-key}") String apiKey) {
		this.expected = digest(apiKey);
	}

	public void check(String provided) {
		if (provided == null || !MessageDigest.isEqual(expected, digest(provided))) {
			throw new ForbiddenException();
		}
	}

	private static byte[] digest(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256");
		}
	}

}

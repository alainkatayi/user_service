package com.mywallet.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccessTokenClaims(
		UUID subject,
		String issuer,
		Instant issuedAt,
		Instant expiresAt,
		UUID tokenId,
		String email,
		List<String> roles,
		String keyId) {
}

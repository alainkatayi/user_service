package com.mywallet.user.service;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mywallet.user.config.AppProperties;
import com.mywallet.user.config.JwtKeyStore;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.exception.InvalidAccessTokenException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;

@Service
public class JwtService {

	private final JwtKeyStore keyStore;
	private final AppProperties properties;
	private final Clock clock;

	public JwtService(JwtKeyStore keyStore, AppProperties properties, Clock clock) {
		this.keyStore = keyStore;
		this.properties = properties;
		this.clock = clock;
	}

	public IssuedAccessToken issueAccessToken(AppUser user) {
		Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = issuedAt.plus(properties.jwt().accessTtl());
		UUID tokenId = UUID.randomUUID();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(properties.jwt().issuer())
				.subject(user.getId().toString())
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(expiresAt))
				.jwtID(tokenId.toString())
				.claim("email", user.getEmail())
				.claim("roles", List.of("USER"))
				.build();
		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(keyStore.keyId())
				.build();
		SignedJWT jwt = new SignedJWT(header, claims);
		try {
			jwt.sign(new RSASSASigner(keyStore.privateKey()));
		}
		catch (JOSEException exception) {
			throw new IllegalStateException("Signature du jeton d'accès impossible", exception);
		}
		return new IssuedAccessToken(jwt.serialize(), properties.jwt().accessTtl().toSeconds());
	}

	public AccessTokenClaims verify(String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidAccessTokenException();
		}
		SignedJWT jwt = signedToken(token);
		if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
			throw new InvalidAccessTokenException();
		}
		if (!keyStore.keyId().equals(jwt.getHeader().getKeyID())) {
			throw new InvalidAccessTokenException();
		}
		try {
			if (!jwt.verify(new RSASSAVerifier(keyStore.publicKey()))) {
				throw new InvalidAccessTokenException();
			}
			return readClaims(jwt.getJWTClaimsSet(), jwt.getHeader().getKeyID());
		}
		catch (JOSEException | ParseException exception) {
			throw new InvalidAccessTokenException();
		}
	}

	public Map<String, Object> jsonWebKeySet() {
		Map<String, Object> key = new LinkedHashMap<>();
		key.put("kty", "RSA");
		key.put("kid", keyStore.keyId());
		key.put("use", "sig");
		key.put("alg", "RS256");
		key.put("n", keyStore.modulus());
		key.put("e", keyStore.exponent());
		return Map.of("keys", List.of(key));
	}

	private SignedJWT signedToken(String token) {
		try {
			JWT parsed = JWTParser.parse(token);
			if (parsed instanceof SignedJWT signed) {
				return signed;
			}
		}
		catch (ParseException exception) {
			throw new InvalidAccessTokenException();
		}
		throw new InvalidAccessTokenException();
	}

	private AccessTokenClaims readClaims(JWTClaimsSet claims, String keyId) throws ParseException {
		String issuer = claims.getIssuer();
		if (!properties.jwt().issuer().equals(issuer)) {
			throw new InvalidAccessTokenException();
		}
		Date expiration = claims.getExpirationTime();
		Date issued = claims.getIssueTime();
		if (expiration == null || issued == null) {
			throw new InvalidAccessTokenException();
		}
		Instant expiresAt = expiration.toInstant();
		if (!clock.instant().isBefore(expiresAt)) {
			throw new InvalidAccessTokenException();
		}
		String subject = claims.getSubject();
		String tokenId = claims.getJWTID();
		String email = claims.getStringClaim("email");
		List<String> roles = claims.getStringListClaim("roles");
		if (subject == null || tokenId == null || email == null || roles == null || roles.isEmpty()) {
			throw new InvalidAccessTokenException();
		}
		try {
			return new AccessTokenClaims(
					UUID.fromString(subject),
					issuer,
					issued.toInstant(),
					expiresAt,
					UUID.fromString(tokenId),
					email,
					List.copyOf(roles),
					keyId);
		}
		catch (IllegalArgumentException exception) {
			throw new InvalidAccessTokenException();
		}
	}

}

package com.mywallet.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mywallet.user.config.AppProperties;
import com.mywallet.user.dto.FieldViolation;
import com.mywallet.user.dto.LoginResponse;
import com.mywallet.user.dto.RefreshTokenRequest;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.entity.RefreshToken;
import com.mywallet.user.entity.UserStatus;
import com.mywallet.user.exception.InvalidRefreshTokenException;
import com.mywallet.user.exception.RequestValidationException;
import com.mywallet.user.repository.RefreshTokenRepository;
import com.mywallet.user.repository.UserRepository;

/**
 * Le jeton en clair n'existe qu'en mémoire le temps de la réponse. La base ne reçoit que son hash.
 */
@Service
public class RefreshTokenService {

	private static final int TOKEN_BYTES = 32;

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final AppProperties properties;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public RefreshTokenService(
			RefreshTokenRepository refreshTokenRepository,
			UserRepository userRepository,
			JwtService jwtService,
			AppProperties properties,
			Clock clock) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
		this.properties = properties;
		this.clock = clock;
	}

	public LoginResponse issueSession(AppUser user) {
		IssuedAccessToken access = jwtService.issueAccessToken(user);
		String refreshToken = persist(user.getId());
		return new LoginResponse(access.token(), refreshToken, "Bearer", access.expiresInSeconds());
	}

	public LoginResponse refresh(RefreshTokenRequest request) {
		String presented = requireToken(request);
		String hash = sha256Hex(presented);
		Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash);
		if (found.isEmpty()) {
			throw new InvalidRefreshTokenException();
		}
		RefreshToken stored = found.get();
		Instant now = clock.instant();
		if (stored.getRevokedAt() != null) {
			refreshTokenRepository.revokeAllActive(stored.getUserId(), now);
			throw new InvalidRefreshTokenException();
		}
		if (!stored.getExpiresAt().isAfter(now)) {
			throw new InvalidRefreshTokenException();
		}
		AppUser user = userRepository.findById(stored.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new InvalidRefreshTokenException();
		}
		if (refreshTokenRepository.revokeIfActive(hash, now) == 0) {
			throw new InvalidRefreshTokenException();
		}
		return issueSession(user);
	}

	public void logout(RefreshTokenRequest request) {
		String presented = requireToken(request);
		refreshTokenRepository.revoke(sha256Hex(presented), clock.instant());
	}

	private String persist(UUID userId) {
		Instant now = clock.instant();
		String token = newToken();
		refreshTokenRepository.save(RefreshToken.issue(
				UUID.randomUUID(),
				userId,
				sha256Hex(token),
				now.plus(properties.jwt().refreshTtl()),
				now));
		return token;
	}

	private String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String requireToken(RefreshTokenRequest request) {
		if (request == null || request.refreshToken() == null || request.refreshToken().isEmpty()) {
			throw new RequestValidationException(List.of(
					new FieldViolation("refreshToken", "Le jeton de rafraîchissement est obligatoire.")));
		}
		return request.refreshToken();
	}

	private static String sha256Hex(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 indisponible", exception);
		}
	}

}

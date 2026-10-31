package com.mywallet.user.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.mywallet.user.config.AppProperties;
import com.mywallet.user.dto.FieldViolation;
import com.mywallet.user.dto.LoginRequest;
import com.mywallet.user.dto.LoginResponse;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.entity.UserStatus;
import com.mywallet.user.exception.InvalidCredentialsException;
import com.mywallet.user.exception.RequestValidationException;
import com.mywallet.user.exception.UserSuspendedException;
import com.mywallet.user.repository.UserRepository;

/** Lecture, BCrypt, puis mise à jour du compteur : trois transactions courtes, sans connexion JDBC pendant BCrypt. */
@Service
public class LoginService {

	private final UserRepository userRepository;
	private final BCryptPasswordEncoder passwordEncoder;
	private final Clock clock;
	private final RefreshTokenService refreshTokenService;
	private final AppProperties properties;
	private final String dummyPasswordHash;

	public LoginService(
			UserRepository userRepository,
			BCryptPasswordEncoder passwordEncoder,
			Clock clock,
			RefreshTokenService refreshTokenService,
			AppProperties properties) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.refreshTokenService = refreshTokenService;
		this.properties = properties;
		this.dummyPasswordHash = passwordEncoder.encode("user-service-dummy-login-hash");
	}

	public LoginResponse login(LoginRequest request) {
		validate(request);
		String email = RegistrationNormalizer.canonicalEmail(request.email());
		String password = request.password();
		Optional<AppUser> found = userRepository.findByEmail(email);
		if (found.isEmpty()) {
			passwordEncoder.matches(password, dummyPasswordHash);
			throw new InvalidCredentialsException();
		}
		AppUser user = found.get();
		boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
		Instant now = clock.instant();
		if (isLocked(user, now)) {
			throw new InvalidCredentialsException();
		}
		if (!passwordMatches) {
			userRepository.registerFailedLogin(
					user.getId(),
					now,
					properties.login().maxFailedAttempts(),
					now.plus(properties.login().lockDuration()));
			throw new InvalidCredentialsException();
		}
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new UserSuspendedException();
		}
		if (userRepository.resetLoginFailures(user.getId(), now) == 0) {
			throw new InvalidCredentialsException();
		}
		return refreshTokenService.issueSession(user);
	}

	private static void validate(LoginRequest request) {
		List<FieldViolation> errors = new ArrayList<>();
		if (request == null || request.email() == null || request.email().trim().isEmpty()) {
			errors.add(new FieldViolation("email", "L'adresse e-mail est obligatoire."));
		}
		if (request == null || request.password() == null || request.password().isEmpty()) {
			errors.add(new FieldViolation("password", "Le mot de passe est obligatoire."));
		}
		if (!errors.isEmpty()) {
			throw new RequestValidationException(errors);
		}
	}

	private static boolean isLocked(AppUser user, Instant now) {
		return user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
	}

}

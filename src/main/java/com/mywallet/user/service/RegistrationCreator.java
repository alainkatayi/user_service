package com.mywallet.user.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mywallet.user.dto.RegisterRequest;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.exception.DuplicatePhoneException;
import com.mywallet.user.exception.EmailAlreadyUsedException;
import com.mywallet.user.exception.RegistrationValidationException;
import com.mywallet.user.repository.UserRepository;
import com.mywallet.user.service.RegistrationNormalizer.NormalizedRegistration;
import com.mywallet.user.service.RegistrationNormalizer.Result;

@Component
@Transactional
public class RegistrationCreator {

	private final UserRepository userRepository;
	private final BCryptPasswordEncoder passwordEncoder;
	private final Clock clock;

	public RegistrationCreator(
			UserRepository userRepository,
			BCryptPasswordEncoder passwordEncoder,
			Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * Une seule tentative. saveAndFlush fait surgir la violation d'unicité dans cette
	 * méthode, avant la clôture de la transaction. existsByEmail et existsByPhone ne
	 * servent qu'à éviter un hash BCrypt inutile : seule la contrainte d'unicité fait foi.
	 */
	public AppUser create(RegisterRequest request) {
		Result result = RegistrationNormalizer.normalize(
				request.email(),
				request.password(),
				request.firstName(),
				request.lastName(),
				request.phone());
		if (!result.valid()) {
			throw new RegistrationValidationException(result.errors());
		}
		NormalizedRegistration input = result.value();
		if (userRepository.existsByEmail(input.email())) {
			throw new EmailAlreadyUsedException();
		}
		if (userRepository.existsByPhone(input.phone())) {
			throw new DuplicatePhoneException();
		}
		String passwordHash = passwordEncoder.encode(input.password());
		AppUser user = AppUser.register(
				UUID.randomUUID(),
				input.email(),
				passwordHash,
				input.firstName(),
				input.lastName(),
				input.phone(),
				clock.instant());
		return userRepository.saveAndFlush(user);
	}

}

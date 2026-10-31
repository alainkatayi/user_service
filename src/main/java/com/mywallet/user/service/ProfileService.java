package com.mywallet.user.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.mywallet.user.dto.UpdateProfileRequest;
import com.mywallet.user.dto.UserResponse;
import com.mywallet.user.exception.ConcurrentUpdateException;
import com.mywallet.user.exception.DuplicatePhoneException;
import com.mywallet.user.exception.RequestValidationException;
import com.mywallet.user.exception.UserNotFoundException;
import com.mywallet.user.repository.UserRepository;
import com.mywallet.user.service.RegistrationNormalizer.ProfileResult;

/**
 * Pas de @Transactional : la traduction de la violation d'unicité se fait après
 * l'annulation de la transaction de ProfileUpdater.
 */
@Service
public class ProfileService {

	private final UserRepository userRepository;
	private final ProfileUpdater profileUpdater;
	private final Clock clock;

	public ProfileService(UserRepository userRepository, ProfileUpdater profileUpdater, Clock clock) {
		this.userRepository = userRepository;
		this.profileUpdater = profileUpdater;
		this.clock = clock;
	}

	public UserResponse get(UUID userId) {
		return userRepository.findById(userId).map(UserResponse::from).orElseThrow(UserNotFoundException::new);
	}

	public UserResponse update(UUID userId, UpdateProfileRequest request) {
		ProfileResult result = RegistrationNormalizer.normalizeProfile(
				request == null ? null : request.firstName(),
				request == null ? null : request.lastName(),
				request == null ? null : request.phone());
		if (!result.valid()) {
			throw new RequestValidationException(result.errors());
		}
		try {
			return profileUpdater.update(userId, result.value(), clock.instant());
		}
		catch (DataIntegrityViolationException exception) {
			if (RegistrationService.PHONE_UNIQUE_CONSTRAINT.equals(DataIntegrityConstraints.name(exception))) {
				throw new DuplicatePhoneException();
			}
			throw exception;
		}
		catch (ObjectOptimisticLockingFailureException exception) {
			throw new ConcurrentUpdateException();
		}
	}

}

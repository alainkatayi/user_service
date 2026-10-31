package com.mywallet.user.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mywallet.user.dto.UserResponse;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.entity.UserStatus;
import com.mywallet.user.exception.UserNotFoundException;
import com.mywallet.user.exception.UserSuspendedException;
import com.mywallet.user.repository.UserRepository;
import com.mywallet.user.service.RegistrationNormalizer.NormalizedProfile;

@Component
public class ProfileUpdater {

	private final UserRepository userRepository;

	public ProfileUpdater(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional
	public UserResponse update(UUID userId, NormalizedProfile profile, Instant now) {
		AppUser user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new UserSuspendedException();
		}
		user.updateProfile(profile.firstName(), profile.lastName(), profile.phone(), now);
		userRepository.saveAndFlush(user);
		return UserResponse.from(user);
	}

}

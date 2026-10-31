package com.mywallet.user.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mywallet.user.entity.AppUser;
import com.mywallet.user.entity.UserStatus;
import com.mywallet.user.exception.UserNotFoundException;
import com.mywallet.user.exception.UserSuspendedException;
import com.mywallet.user.repository.RefreshTokenRepository;
import com.mywallet.user.repository.UserRepository;

@Component
public class PasswordChanger {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;

	public PasswordChanger(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Transactional
	public void apply(UUID userId, String newHash, Instant now) {
		AppUser user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new UserSuspendedException();
		}
		user.changePassword(newHash, now);
		userRepository.saveAndFlush(user);
		refreshTokenRepository.revokeAllActive(userId, now);
	}

}

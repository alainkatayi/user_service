package com.mywallet.user.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.mywallet.user.dto.ChangePasswordRequest;
import com.mywallet.user.dto.FieldViolation;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.entity.UserStatus;
import com.mywallet.user.exception.ConcurrentUpdateException;
import com.mywallet.user.exception.RequestValidationException;
import com.mywallet.user.exception.SamePasswordException;
import com.mywallet.user.exception.UserNotFoundException;
import com.mywallet.user.exception.UserSuspendedException;
import com.mywallet.user.exception.WrongCurrentPasswordException;
import com.mywallet.user.repository.UserRepository;

/** BCrypt (vérification puis hachage) se fait avant la transaction d'écriture. */
@Service
public class PasswordService {

	private final UserRepository userRepository;
	private final PasswordChanger passwordChanger;
	private final BCryptPasswordEncoder passwordEncoder;
	private final Clock clock;

	public PasswordService(
			UserRepository userRepository,
			PasswordChanger passwordChanger,
			BCryptPasswordEncoder passwordEncoder,
			Clock clock) {
		this.userRepository = userRepository;
		this.passwordChanger = passwordChanger;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	public void change(UUID userId, ChangePasswordRequest request) {
		List<FieldViolation> errors = RegistrationNormalizer.validateNewPassword(
				request == null ? null : request.newPassword());
		if (!errors.isEmpty()) {
			throw new RequestValidationException(errors);
		}
		AppUser user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new UserSuspendedException();
		}
		String current = request.currentPassword() == null ? "" : request.currentPassword();
		if (!passwordEncoder.matches(current, user.getPasswordHash())) {
			throw new WrongCurrentPasswordException();
		}
		if (current.equals(request.newPassword())) {
			throw new SamePasswordException();
		}
		String newHash = passwordEncoder.encode(request.newPassword());
		try {
			passwordChanger.apply(userId, newHash, clock.instant());
		}
		catch (ObjectOptimisticLockingFailureException exception) {
			throw new ConcurrentUpdateException();
		}
	}

}

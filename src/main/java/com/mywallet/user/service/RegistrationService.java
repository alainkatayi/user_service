package com.mywallet.user.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.mywallet.user.dto.RegisterRequest;
import com.mywallet.user.dto.UserResponse;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.exception.DuplicatePhoneException;
import com.mywallet.user.exception.EmailAlreadyUsedException;
import com.mywallet.user.exception.RegistrationValidationException;

/**
 * Pas de @Transactional ici. Une transaction qui a rencontré une erreur de base est
 * condamnée au rollback et ne peut plus être réutilisée : la traduction de
 * DataIntegrityViolationException se fait donc dans ce bean, après que RegistrationCreator
 * a terminé et annulé sa transaction.
 */
@Service
public class RegistrationService {

	/**
	 * Noms réels des contraintes UNIQUE de colonne sous PostgreSQL
	 * ({@code table_colonne_key}), confirmés sur le catalogue pg_constraint.
	 */
	public static final String EMAIL_UNIQUE_CONSTRAINT = "app_user_email_key";
	public static final String PHONE_UNIQUE_CONSTRAINT = "app_user_phone_key";

	private final RegistrationCreator registrationCreator;

	public RegistrationService(RegistrationCreator registrationCreator) {
		this.registrationCreator = registrationCreator;
	}

	public UserResponse register(RegisterRequest request) {
		if (request == null) {
			throw new RegistrationValidationException(
					RegistrationNormalizer.normalize(null, null, null, null, null).errors());
		}
		try {
			AppUser user = registrationCreator.create(request);
			return UserResponse.from(user);
		}
		catch (DataIntegrityViolationException exception) {
			String constraint = DataIntegrityConstraints.name(exception);
			if (EMAIL_UNIQUE_CONSTRAINT.equals(constraint)) {
				throw new EmailAlreadyUsedException();
			}
			if (PHONE_UNIQUE_CONSTRAINT.equals(constraint)) {
				throw new DuplicatePhoneException();
			}
			throw exception;
		}
	}

}

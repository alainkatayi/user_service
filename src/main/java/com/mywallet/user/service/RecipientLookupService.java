package com.mywallet.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mywallet.user.dto.InternalUserResponse;
import com.mywallet.user.dto.RecipientLookupResponse;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.entity.UserStatus;
import com.mywallet.user.exception.SelfTransferException;
import com.mywallet.user.exception.UserNotFoundException;
import com.mywallet.user.exception.UserSuspendedException;
import com.mywallet.user.repository.UserRepository;

@Service
public class RecipientLookupService {

	private final UserRepository users;

	public RecipientLookupService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public RecipientLookupResponse lookup(UUID callerId, String rawPhone) {
		String phone = RegistrationNormalizer.requirePhone(rawPhone);
		AppUser recipient = users.findByPhone(phone).orElseThrow(UserNotFoundException::new);
		if (recipient.getId().equals(callerId)) {
			throw new SelfTransferException();
		}
		if (recipient.getStatus() == UserStatus.SUSPENDED) {
			throw new UserSuspendedException();
		}
		return new RecipientLookupResponse(recipient.getFirstName(), recipient.getLastName(), recipient.getPhone());
	}

	@Transactional(readOnly = true)
	public InternalUserResponse byPhone(String rawPhone) {
		String phone = RegistrationNormalizer.requirePhone(rawPhone);
		AppUser user = users.findByPhone(phone).orElseThrow(UserNotFoundException::new);
		return InternalUserResponse.from(user);
	}

	@Transactional(readOnly = true)
	public InternalUserResponse byId(UUID userId) {
		AppUser user = users.findById(userId).orElseThrow(UserNotFoundException::new);
		return InternalUserResponse.from(user);
	}

}

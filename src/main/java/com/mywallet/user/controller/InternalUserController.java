package com.mywallet.user.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mywallet.user.config.InternalApiKey;
import com.mywallet.user.dto.InternalUserResponse;
import com.mywallet.user.exception.UserNotFoundException;
import com.mywallet.user.service.RecipientLookupService;

@RestController
public class InternalUserController {

	private final InternalApiKey apiKey;
	private final RecipientLookupService recipients;

	public InternalUserController(InternalApiKey apiKey, RecipientLookupService recipients) {
		this.apiKey = apiKey;
		this.recipients = recipients;
	}

	@GetMapping("/internal/users/by-phone")
	public InternalUserResponse byPhone(
			@RequestParam String phone,
			@RequestHeader(value = "X-Internal-Key", required = false) String key) {
		apiKey.check(key);
		return recipients.byPhone(phone);
	}

	@GetMapping("/internal/users/{userId}")
	public InternalUserResponse byId(
			@PathVariable String userId,
			@RequestHeader(value = "X-Internal-Key", required = false) String key) {
		apiKey.check(key);
		try {
			return recipients.byId(UUID.fromString(userId));
		}
		catch (IllegalArgumentException exception) {
			throw new UserNotFoundException();
		}
	}

}

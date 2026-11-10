package com.mywallet.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mywallet.user.dto.ChangePasswordRequest;
import com.mywallet.user.dto.PhoneLookupRequest;
import com.mywallet.user.dto.RecipientLookupResponse;
import com.mywallet.user.dto.UpdateProfileRequest;
import com.mywallet.user.dto.UserResponse;
import com.mywallet.user.security.CurrentUser;
import com.mywallet.user.service.PasswordService;
import com.mywallet.user.service.ProfileService;
import com.mywallet.user.service.RecipientLookupService;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final CurrentUser currentUser;
	private final ProfileService profileService;
	private final PasswordService passwordService;
	private final RecipientLookupService recipients;

	public UserController(
			CurrentUser currentUser,
			ProfileService profileService,
			PasswordService passwordService,
			RecipientLookupService recipients) {
		this.currentUser = currentUser;
		this.profileService = profileService;
		this.passwordService = passwordService;
		this.recipients = recipients;
	}

	@GetMapping("/me")
	public UserResponse me() {
		return profileService.get(currentUser.id());
	}

	@PutMapping("/me")
	public UserResponse update(@RequestBody UpdateProfileRequest request) {
		return profileService.update(currentUser.id(), request);
	}

	@PostMapping("/me/password")
	public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request) {
		passwordService.change(currentUser.id(), request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/lookup")
	public RecipientLookupResponse lookup(@RequestBody(required = false) PhoneLookupRequest body) {
		return recipients.lookup(currentUser.id(), body == null ? null : body.phone());
	}

}

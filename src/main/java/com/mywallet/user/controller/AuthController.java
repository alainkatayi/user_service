package com.mywallet.user.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mywallet.user.dto.RegisterRequest;
import com.mywallet.user.dto.UserResponse;
import com.mywallet.user.service.RegistrationService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final RegistrationService registrationService;

	public AuthController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
		UserResponse body = registrationService.register(request);
		return ResponseEntity.created(URI.create("/api/users/me")).body(body);
	}

}

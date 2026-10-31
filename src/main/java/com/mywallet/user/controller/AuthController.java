package com.mywallet.user.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mywallet.user.dto.LoginRequest;
import com.mywallet.user.dto.LoginResponse;
import com.mywallet.user.dto.RefreshTokenRequest;
import com.mywallet.user.dto.RegisterRequest;
import com.mywallet.user.dto.UserResponse;
import com.mywallet.user.service.LoginService;
import com.mywallet.user.service.RefreshTokenService;
import com.mywallet.user.service.RegistrationService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final RegistrationService registrationService;
	private final LoginService loginService;
	private final RefreshTokenService refreshTokenService;

	public AuthController(
			RegistrationService registrationService,
			LoginService loginService,
			RefreshTokenService refreshTokenService) {
		this.registrationService = registrationService;
		this.loginService = loginService;
		this.refreshTokenService = refreshTokenService;
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
		UserResponse body = registrationService.register(request);
		return ResponseEntity.created(URI.create("/api/users/me")).body(body);
	}

	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request) {
		return loginService.login(request);
	}

	@PostMapping("/refresh")
	public LoginResponse refresh(@RequestBody RefreshTokenRequest request) {
		return refreshTokenService.refresh(request);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
		refreshTokenService.logout(request);
		return ResponseEntity.noContent().build();
	}

}

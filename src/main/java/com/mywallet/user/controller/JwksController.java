package com.mywallet.user.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mywallet.user.service.JwtService;

@RestController
public class JwksController {

	private final JwtService jwtService;

	public JwksController(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> jwks() {
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
				.body(jwtService.jsonWebKeySet());
	}

}
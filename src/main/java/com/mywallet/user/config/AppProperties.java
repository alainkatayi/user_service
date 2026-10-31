package com.mywallet.user.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Login login) {

	public record Jwt(String issuer, Duration accessTtl, Duration refreshTtl, String keysDir) {
	}

	public record Login(int maxFailedAttempts, Duration lockDuration) {
	}

}

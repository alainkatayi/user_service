package com.mywallet.user.security;

import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.mywallet.user.exception.UnauthenticatedException;

@Component
public class CurrentUser {

	public UUID id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			throw new UnauthenticatedException();
		}
		String subject = authentication.getPrincipal() instanceof Jwt jwt
				? jwt.getSubject()
				: authentication.getName();
		try {
			return UUID.fromString(subject);
		}
		catch (IllegalArgumentException | NullPointerException exception) {
			throw new UnauthenticatedException();
		}
	}

}

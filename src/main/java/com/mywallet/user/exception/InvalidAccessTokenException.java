package com.mywallet.user.exception;

public class InvalidAccessTokenException extends RuntimeException {

	public InvalidAccessTokenException() {
		super("Jeton d'accès invalide.");
	}

}

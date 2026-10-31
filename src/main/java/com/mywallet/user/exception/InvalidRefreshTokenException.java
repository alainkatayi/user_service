package com.mywallet.user.exception;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Jeton de rafraîchissement invalide.");
	}

}

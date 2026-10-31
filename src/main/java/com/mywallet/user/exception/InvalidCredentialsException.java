package com.mywallet.user.exception;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Identifiants invalides.");
	}

}

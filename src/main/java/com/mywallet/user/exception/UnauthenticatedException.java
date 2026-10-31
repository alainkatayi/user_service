package com.mywallet.user.exception;

public class UnauthenticatedException extends RuntimeException {

	public UnauthenticatedException() {
		super("Authentification requise.");
	}

}

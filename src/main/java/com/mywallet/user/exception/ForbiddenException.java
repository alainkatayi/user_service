package com.mywallet.user.exception;

public class ForbiddenException extends RuntimeException {

	public ForbiddenException() {
		super("Accès refusé.");
	}

}

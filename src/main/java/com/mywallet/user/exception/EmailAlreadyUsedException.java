package com.mywallet.user.exception;

public class EmailAlreadyUsedException extends RuntimeException {

	public EmailAlreadyUsedException() {
		super("Cette adresse e-mail est déjà utilisée.");
	}

}

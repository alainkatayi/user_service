package com.mywallet.user.exception;

public class DuplicatePhoneException extends RuntimeException {

	public DuplicatePhoneException() {
		super("Ce numéro de téléphone est déjà utilisé.");
	}

}

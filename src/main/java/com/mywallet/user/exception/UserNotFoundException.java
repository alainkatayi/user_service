package com.mywallet.user.exception;

public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException() {
		super("Utilisateur introuvable.");
	}

}

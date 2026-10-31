package com.mywallet.user.exception;

public class WrongCurrentPasswordException extends RuntimeException {

	public WrongCurrentPasswordException() {
		super("Le mot de passe actuel est incorrect.");
	}

}

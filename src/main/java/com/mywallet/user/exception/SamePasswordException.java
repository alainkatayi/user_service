package com.mywallet.user.exception;

public class SamePasswordException extends RuntimeException {

	public SamePasswordException() {
		super("Le nouveau mot de passe doit être différent de l'actuel.");
	}

}

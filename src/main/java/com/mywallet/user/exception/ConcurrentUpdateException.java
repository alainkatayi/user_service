package com.mywallet.user.exception;

public class ConcurrentUpdateException extends RuntimeException {

	public ConcurrentUpdateException() {
		super("La fiche a été modifiée entre-temps.");
	}

}

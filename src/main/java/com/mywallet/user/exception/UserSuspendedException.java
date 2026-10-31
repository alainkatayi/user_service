package com.mywallet.user.exception;

public class UserSuspendedException extends RuntimeException {

	public UserSuspendedException() {
		super("Ce compte est suspendu.");
	}

}

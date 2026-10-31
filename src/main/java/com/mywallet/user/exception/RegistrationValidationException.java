package com.mywallet.user.exception;

import java.util.List;

import com.mywallet.user.dto.FieldViolation;

public class RegistrationValidationException extends RuntimeException {

	private final List<FieldViolation> errors;

	public RegistrationValidationException(List<FieldViolation> errors) {
		super("Un ou plusieurs champs sont invalides.");
		this.errors = List.copyOf(errors);
	}

	public List<FieldViolation> errors() {
		return errors;
	}

}

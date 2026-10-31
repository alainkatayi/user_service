package com.mywallet.user.exception;

import java.util.List;

import com.mywallet.user.dto.FieldViolation;

public class RegistrationValidationException extends RequestValidationException {

	public RegistrationValidationException(List<FieldViolation> errors) {
		super(errors);
	}

}

package com.mywallet.user.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class DataIntegrityConstraints {

	private static final Pattern QUOTED_NAME = Pattern.compile(
			"(?:constraint|contrainte(?:\\s+unique)?)\\s+[\"«]([^\"»]+)[\"»]");

	private DataIntegrityConstraints() {
	}

	public static String name(DataIntegrityViolationException exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
				return unqualified(violation.getConstraintName());
			}
			current = current.getCause();
		}
		String message = exception.getMostSpecificCause().getMessage();
		if (message == null) {
			return null;
		}
		Matcher matcher = QUOTED_NAME.matcher(message);
		if (matcher.find()) {
			return unqualified(matcher.group(1));
		}
		return null;
	}

	private static String unqualified(String name) {
		int dot = name.lastIndexOf('.');
		return dot >= 0 ? name.substring(dot + 1) : name;
	}

}

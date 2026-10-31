package com.mywallet.user.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import jakarta.servlet.http.HttpServletRequest;

public final class ProblemDetails {

	private ProblemDetails() {
	}

	public static ProblemDetail of(
			HttpStatus status,
			String title,
			String detail,
			String code,
			HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		return problem;
	}

}

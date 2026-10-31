package com.mywallet.user.exception;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(RequestValidationException.class)
	public ProblemDetail handleValidation(RequestValidationException exception, HttpServletRequest request) {
		ProblemDetail problem = problem(
				HttpStatus.BAD_REQUEST,
				"Requête invalide",
				"Un ou plusieurs champs sont invalides.",
				"VALIDATION_ERROR",
				request);
		problem.setProperty("errors", exception.errors());
		return problem;
	}

	@ExceptionHandler(EmailAlreadyUsedException.class)
	public ProblemDetail handleEmailAlreadyUsed(HttpServletRequest request) {
		return problem(
				HttpStatus.CONFLICT,
				"Conflit",
				"Cette adresse e-mail est déjà utilisée.",
				"EMAIL_ALREADY_USED",
				request);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ProblemDetail handleInvalidCredentials(HttpServletRequest request) {
		return problem(
				HttpStatus.UNAUTHORIZED,
				"Non autorisé",
				"Identifiants invalides.",
				"INVALID_CREDENTIALS",
				request);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ProblemDetail handleInvalidRefreshToken(HttpServletRequest request) {
		return problem(
				HttpStatus.UNAUTHORIZED,
				"Non autorisé",
				"Jeton de rafraîchissement invalide.",
				"INVALID_REFRESH_TOKEN",
				request);
	}

	@ExceptionHandler(UserSuspendedException.class)
	public ProblemDetail handleUserSuspended(HttpServletRequest request) {
		return problem(
				HttpStatus.FORBIDDEN,
				"Interdit",
				"Ce compte est suspendu.",
				"USER_SUSPENDED",
				request);
	}

	@ExceptionHandler(DuplicatePhoneException.class)
	public ProblemDetail handleDuplicatePhone(HttpServletRequest request) {
		return problem(
				HttpStatus.CONFLICT,
				"Conflit",
				"Ce numéro de téléphone est déjà utilisé.",
				"DUPLICATE_PHONE",
				request);
	}

	/**
	 * Le message de HttpMessageNotReadableException cite un extrait du corps :
	 * on ne le logue pas, pour ne pas y écrire le mot de passe.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleMalformed(HttpServletRequest request) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"Requête mal formée",
				"Le corps de la requête est illisible.",
				"MALFORMED_REQUEST",
				request);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Erreur interne inattendue ({})", exception.getClass().getName(), exception);
		return problem(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Erreur interne",
				"Une erreur interne est survenue.",
				"INTERNAL_ERROR",
				request);
	}

	private static ProblemDetail problem(
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

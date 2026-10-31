package com.mywallet.user.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.mywallet.user.dto.FieldViolation;

/**
 * Normalise et valide une inscription. Aucune règle n'est appliquée dans le contrôleur.
 * Une seule erreur est retenue par champ : la première règle violée.
 */
public final class RegistrationNormalizer {

	public static final int EMAIL_MAX_LENGTH = 150;
	public static final int NAME_MAX_LENGTH = 100;
	public static final int PASSWORD_MIN_LENGTH = 8;
	public static final int PASSWORD_MAX_LENGTH = 64;
	public static final int BCRYPT_MAX_BYTES = 72;

	private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
	private static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

	private RegistrationNormalizer() {
	}

	public static Result normalize(
			String email,
			String password,
			String firstName,
			String lastName,
			String phone) {
		List<FieldViolation> errors = new ArrayList<>();
		String normalizedEmail = normalizeEmail(email, errors);
		validatePassword(password, errors);
		String normalizedFirstName = normalizeName(firstName, "firstName", "Le prénom", errors);
		String normalizedLastName = normalizeName(lastName, "lastName", "Le nom", errors);
		String normalizedPhone = normalizePhone(phone, errors);
		if (!errors.isEmpty()) {
			return new Result(null, List.copyOf(errors));
		}
		return new Result(
				new NormalizedRegistration(
						normalizedEmail,
						password,
						normalizedFirstName,
						normalizedLastName,
						normalizedPhone),
				List.of());
	}

	public static String canonicalEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static String normalizeEmail(String email, List<FieldViolation> errors) {
		if (email == null || email.trim().isEmpty()) {
			errors.add(new FieldViolation("email", "L'adresse e-mail est obligatoire."));
			return null;
		}
		String normalized = canonicalEmail(email);
		if (normalized.length() > EMAIL_MAX_LENGTH) {
			errors.add(new FieldViolation("email", "L'adresse e-mail ne doit pas dépasser 150 caractères."));
			return null;
		}
		if (!EMAIL.matcher(normalized).matches()) {
			errors.add(new FieldViolation("email", "L'adresse e-mail est invalide."));
			return null;
		}
		return normalized;
	}

	private static void validatePassword(String password, List<FieldViolation> errors) {
		if (password == null || password.isEmpty()) {
			errors.add(new FieldViolation("password", "Le mot de passe est obligatoire."));
			return;
		}
		if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
			errors.add(new FieldViolation("password", "Le mot de passe doit contenir entre 8 et 64 caractères."));
			return;
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
			errors.add(new FieldViolation("password", "Le mot de passe ne doit pas dépasser 72 octets UTF-8."));
			return;
		}
		boolean letter = false;
		boolean digit = false;
		for (int offset = 0; offset < password.length();) {
			int codePoint = password.codePointAt(offset);
			if (Character.isLetter(codePoint)) {
				letter = true;
			}
			if (codePoint >= '0' && codePoint <= '9') {
				digit = true;
			}
			offset += Character.charCount(codePoint);
		}
		if (!letter || !digit) {
			errors.add(new FieldViolation("password", "Le mot de passe doit contenir au moins une lettre et un chiffre."));
		}
	}

	private static String normalizeName(String value, String field, String label, List<FieldViolation> errors) {
		if (value == null || value.trim().isEmpty()) {
			errors.add(new FieldViolation(field, label + " est obligatoire."));
			return null;
		}
		String normalized = value.trim();
		if (normalized.length() > NAME_MAX_LENGTH) {
			errors.add(new FieldViolation(field, label + " ne doit pas dépasser 100 caractères."));
			return null;
		}
		return normalized;
	}

	private static String normalizePhone(String phone, List<FieldViolation> errors) {
		if (phone == null || phone.isBlank()) {
			errors.add(new FieldViolation("phone", "Le numéro de téléphone est obligatoire."));
			return null;
		}
		String normalized = phone.replace(" ", "").replace("-", "");
		if (!PHONE.matcher(normalized).matches()) {
			errors.add(new FieldViolation("phone", "Le numéro de téléphone est invalide."));
			return null;
		}
		return normalized;
	}

	public record NormalizedRegistration(
			String email,
			String password,
			String firstName,
			String lastName,
			String phone) {
	}

	public record Result(NormalizedRegistration value, List<FieldViolation> errors) {

		public Result {
			errors = List.copyOf(errors);
		}

		public boolean valid() {
			return errors.isEmpty();
		}

	}

}

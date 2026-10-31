package com.mywallet.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mywallet.user.dto.FieldViolation;
import com.mywallet.user.service.RegistrationNormalizer.Result;

class RegistrationNormalizerTest {

	@Test
	void trimsAndLowercasesEmail() {
		Result result = normalize("  Jean.Dupont@Example.COM  ", "Motdepasse1", "Ada", "Lovelace", "+33612345678");

		assertThat(result.valid()).isTrue();
		assertThat(result.value().email()).isEqualTo("jean.dupont@example.com");
	}

	@Test
	void rejectsInvalidEmail() {
		Result result = normalize("pas-un-email", "Motdepasse1", "Ada", "Lovelace", "+33612345678");

		assertThat(result.errors()).containsExactly(
				new FieldViolation("email", "L'adresse e-mail est invalide."));
	}

	@Test
	void acceptsEmailOf150CharactersAndRejects151() {
		Result accepted = normalize(emailOfLength(150), "Motdepasse1", "Ada", "Lovelace", "+33612345678");
		Result rejected = normalize(emailOfLength(151), "Motdepasse1", "Ada", "Lovelace", "+33612345678");

		assertThat(accepted.valid()).isTrue();
		assertThat(rejected.errors()).containsExactly(
				new FieldViolation("email", "L'adresse e-mail ne doit pas dépasser 150 caractères."));
	}

	@Test
	void keepsPasswordUntouchedIncludingSurroundingSpaces() {
		String password = "abcdef1 ";

		Result result = normalize("ada@example.com", password, "Ada", "Lovelace", "+33612345678");

		assertThat(result.valid()).isTrue();
		assertThat(result.value().password()).isSameAs(password);
	}

	@Test
	void rejectsPasswordShorterThan8() {
		assertThat(normalize("ada@example.com", "abc12", "Ada", "Lovelace", "+33612345678").errors())
				.containsExactly(new FieldViolation("password", "Le mot de passe doit contenir entre 8 et 64 caractères."));
	}

	@Test
	void rejectsPasswordWithoutDigit() {
		assertThat(normalize("ada@example.com", "abcdefgh", "Ada", "Lovelace", "+33612345678").errors())
				.containsExactly(new FieldViolation("password", "Le mot de passe doit contenir au moins une lettre et un chiffre."));
	}

	@Test
	void rejectsPasswordWithoutLetter() {
		assertThat(normalize("ada@example.com", "12345678", "Ada", "Lovelace", "+33612345678").errors())
				.containsExactly(new FieldViolation("password", "Le mot de passe doit contenir au moins une lettre et un chiffre."));
	}

	@Test
	void accepts64CharactersAndRejects65() {
		String accepted = "a".repeat(63) + "1";
		String rejected = accepted + "b";

		assertThat(accepted).hasSize(64);
		assertThat(rejected).hasSize(65);
		assertThat(normalize("ada@example.com", accepted, "Ada", "Lovelace", "+33612345678").valid()).isTrue();
		assertThat(normalize("ada@example.com", rejected, "Ada", "Lovelace", "+33612345678").errors())
				.containsExactly(new FieldViolation("password", "Le mot de passe doit contenir entre 8 et 64 caractères."));
	}

	@Test
	void accepts72Utf8BytesAndRejects73WithMultibyteCharacters() {
		String accepted = "é".repeat(35) + "a1";
		String rejected = "é".repeat(36) + "1";

		assertThat(accepted.getBytes(StandardCharsets.UTF_8)).hasSize(72);
		assertThat(accepted.length()).isBetween(8, 64);
		assertThat(rejected.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);
		assertThat(rejected.length()).isBetween(8, 64);
		assertThat(normalize("ada@example.com", accepted, "Ada", "Lovelace", "+33612345678").valid()).isTrue();
		assertThat(normalize("ada@example.com", accepted, "Ada", "Lovelace", "+33612345678").value().password())
				.isSameAs(accepted);
		assertThat(normalize("ada@example.com", rejected, "Ada", "Lovelace", "+33612345678").errors())
				.containsExactly(new FieldViolation("password", "Le mot de passe ne doit pas dépasser 72 octets UTF-8."));
	}

	@Test
	void trimsNamesAndRejectsBlankOrTooLongOnes() {
		Result trimmed = normalize("ada@example.com", "Motdepasse1", "  Ada  ", "  Lovelace  ", "+33612345678");
		assertThat(trimmed.value().firstName()).isEqualTo("Ada");
		assertThat(trimmed.value().lastName()).isEqualTo("Lovelace");

		assertThat(normalize("ada@example.com", "Motdepasse1", "   ", "Lovelace", "+33612345678").errors())
				.containsExactly(new FieldViolation("firstName", "Le prénom est obligatoire."));
		assertThat(normalize("ada@example.com", "Motdepasse1", "Ada", "   ", "+33612345678").errors())
				.containsExactly(new FieldViolation("lastName", "Le nom est obligatoire."));

		String hundred = "A".repeat(100);
		assertThat(normalize("ada@example.com", "Motdepasse1", " " + hundred + " ", "Lovelace", "+33612345678").valid())
				.isTrue();
		assertThat(normalize("ada@example.com", "Motdepasse1", "A".repeat(101), "Lovelace", "+33612345678").errors())
				.containsExactly(new FieldViolation("firstName", "Le prénom ne doit pas dépasser 100 caractères."));
		assertThat(normalize("ada@example.com", "Motdepasse1", "Ada", "B".repeat(101), "+33612345678").errors())
				.containsExactly(new FieldViolation("lastName", "Le nom ne doit pas dépasser 100 caractères."));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"+33612345678",
			"+33 6 12 34 56 78",
			"+33-6-12-34-56-78",
			"+33 6-12 34-56 78",
			"+1 202-555-0123",
			"+12345678",
			"+123456789012345"
	})
	void acceptsInternationalPhonesAfterRemovingSpacesAndHyphens(String phone) {
		Result result = normalize("ada@example.com", "Motdepasse1", "Ada", "Lovelace", phone);

		assertThat(result.valid()).isTrue();
		assertThat(result.value().phone()).matches("^\\+[1-9][0-9]{7,14}$");
		assertThat(result.value().phone()).doesNotContain(" ").doesNotContain("-");
	}

	@Test
	void normalizesSpacedPhoneToCompactForm() {
		Result result = normalize("ada@example.com", "Motdepasse1", "Ada", "Lovelace", "+33 6-12 34-56 78");

		assertThat(result.value().phone()).isEqualTo("+33612345678");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"33612345678",
			"+1234567",
			"+33ABCDEFGH",
			"+0123456789",
			"++33612345678",
			"+33 6 12"
	})
	void rejectsInvalidPhones(String phone) {
		assertThat(normalize("ada@example.com", "Motdepasse1", "Ada", "Lovelace", phone).errors())
				.containsExactly(new FieldViolation("phone", "Le numéro de téléphone est invalide."));
	}

	@Test
	void reportsEveryMissingField() {
		Result result = RegistrationNormalizer.normalize(null, null, null, null, null);

		assertThat(result.valid()).isFalse();
		assertThat(result.value()).isNull();
		assertThat(result.errors()).extracting(FieldViolation::field)
				.containsExactly("email", "password", "firstName", "lastName", "phone");
		assertThat(result.errors()).extracting(FieldViolation::message)
				.containsExactly(
						"L'adresse e-mail est obligatoire.",
						"Le mot de passe est obligatoire.",
						"Le prénom est obligatoire.",
						"Le nom est obligatoire.",
						"Le numéro de téléphone est obligatoire.");
	}

	private static Result normalize(String email, String password, String firstName, String lastName, String phone) {
		return RegistrationNormalizer.normalize(email, password, firstName, lastName, phone);
	}

	private static String emailOfLength(int length) {
		String domain = "@example.com";
		return "a".repeat(length - domain.length()) + domain;
	}

}

package com.mywallet.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mywallet.user.dto.FieldViolation;
import com.mywallet.user.dto.UserResponse;
import com.mywallet.user.entity.UserStatus;
import com.mywallet.user.exception.DuplicatePhoneException;
import com.mywallet.user.exception.EmailAlreadyUsedException;
import com.mywallet.user.exception.RegistrationValidationException;
import com.mywallet.user.service.RegistrationService;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

	private static final String BODY = """
			{"email":"ada@example.com","password":"Motdepasse1","firstName":"Ada","lastName":"Lovelace","phone":"+33612345678"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistrationService registrationService;

	@Test
	void registerReturnsCreatedUserWithoutAnyPasswordField() throws Exception {
		UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
		Instant createdAt = Instant.parse("2026-10-31T12:00:00Z");
		when(registrationService.register(any())).thenReturn(new UserResponse(
				id,
				"ada@example.com",
				"Ada",
				"Lovelace",
				"+33612345678",
				UserStatus.ACTIVE,
				createdAt,
				createdAt));

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/users/me")))
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.email").value("ada@example.com"))
				.andExpect(jsonPath("$.firstName").value("Ada"))
				.andExpect(jsonPath("$.lastName").value("Lovelace"))
				.andExpect(jsonPath("$.phone").value("+33612345678"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.createdAt").value("2026-10-31T12:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-10-31T12:00:00Z"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.password_hash").doesNotExist())
				.andExpect(content().string(not(containsString("Motdepasse1"))));
	}

	@Test
	void emailConflictUsesProblemDetail() throws Exception {
		when(registrationService.register(any())).thenThrow(new EmailAlreadyUsedException());

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"))
				.andExpect(content().string(not(containsString("Motdepasse1"))));
	}

	@Test
	void duplicatePhoneUsesProblemDetail() throws Exception {
		when(registrationService.register(any())).thenThrow(new DuplicatePhoneException());

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_PHONE"));
	}

	@Test
	void validationErrorListsFields() throws Exception {
		when(registrationService.register(any())).thenThrow(new RegistrationValidationException(List.of(
				new FieldViolation("email", "L'adresse e-mail est invalide."),
				new FieldViolation("password", "Le mot de passe doit contenir entre 8 et 64 caractères."))));

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder("email", "password")));
	}

	@Test
	void malformedJsonDoesNotEchoTheBody() throws Exception {
		String secret = "MalformeSecret9";

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"" + secret + "\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
				.andExpect(content().string(not(containsString(secret))))
				.andExpect(content().string(not(containsString("HttpMessageNotReadableException"))));
	}

	@Test
	void unexpectedExceptionIsAGenericInternalError() throws Exception {
		when(registrationService.register(any())).thenThrow(new IllegalStateException(
				"org.postgresql.util.PSQLException SELECT password_hash FROM app_user WHERE secret = 'LeakMe123'"));

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("Une erreur interne est survenue."))
				.andExpect(content().string(not(containsString("IllegalStateException"))))
				.andExpect(content().string(not(containsString("PSQLException"))))
				.andExpect(content().string(not(containsString("SELECT"))))
				.andExpect(content().string(not(containsString("LeakMe123"))))
				.andExpect(content().string(not(containsString("password_hash"))))
				.andExpect(content().string(not(containsString("Motdepasse1"))));
	}

}

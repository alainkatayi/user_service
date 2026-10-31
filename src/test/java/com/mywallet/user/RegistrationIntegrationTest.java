package com.mywallet.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.repository.UserRepository;
import com.mywallet.user.service.DataIntegrityConstraints;
import com.mywallet.user.service.RegistrationService;

import org.junit.jupiter.api.extension.ExtendWith;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.hikari.maximum-pool-size=30"
})
@ExtendWith(OutputCaptureExtension.class)
class RegistrationIntegrationTest {

	private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1_000_000);

	private static final Path KEYS_DIR = createKeysDir();

	@DynamicPropertySource
	static void jwtKeys(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.keys-dir", KEYS_DIR::toString);
	}

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private Clock clock;

	@LocalServerPort
	private int port;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Test
	void contextLoads() {
		assertThat(userRepository).isNotNull();
		assertThat(passwordEncoder).isNotNull();
	}

	@Test
	void registersUserAndStoresBcryptHash() {
		String email = "  Jean.Dupont+" + UUID.randomUUID() + "@Example.COM  ";
		String storedEmail = email.trim().toLowerCase(Locale.ROOT);
		String password = "Motdepasse1";
		String phone = uniquePhone();

		ApiResponse response = post(email, password, "  Ada  ", " Lovelace ", phone);

		assertThat(response.status()).isEqualTo(201);
		assertThat(response.location()).endsWith("/api/users/me");

		String body = response.body();
		Map<String, Object> document = JsonPath.read(body, "$");
		assertThat(document.keySet()).containsExactlyInAnyOrder(
				"id", "email", "firstName", "lastName", "phone", "status", "createdAt", "updatedAt");
		assertThat(JsonPath.read(body, "$.email").toString()).isEqualTo(storedEmail);
		assertThat(JsonPath.read(body, "$.firstName").toString()).isEqualTo("Ada");
		assertThat(JsonPath.read(body, "$.lastName").toString()).isEqualTo("Lovelace");
		assertThat(JsonPath.read(body, "$.phone").toString()).isEqualTo(phone);
		assertThat(JsonPath.read(body, "$.status").toString()).isEqualTo("ACTIVE");
		assertThat(JsonPath.read(body, "$.createdAt").toString())
				.isEqualTo(JsonPath.read(body, "$.updatedAt").toString());
		assertThat(body).doesNotContain(password);
		assertThat(body.toLowerCase()).doesNotContain("password");

		Map<String, Object> row = jdbc.queryForMap(
				"select email, password_hash, status, failed_login_attempts, created_at, updated_at from app_user where email = ?",
				storedEmail);
		assertThat(row.get("email")).isEqualTo(storedEmail);
		assertThat(row.get("status")).isEqualTo("ACTIVE");
		assertThat(((Number) row.get("failed_login_attempts")).intValue()).isZero();
		assertThat(row.get("created_at")).isEqualTo(row.get("updated_at"));
		String hash = (String) row.get("password_hash");
		assertThat(hash).matches("\\$2[ab]\\$12\\$.+");
		assertThat(hash).isNotEqualTo(password);
		assertThat(passwordEncoder.matches(password, hash)).isTrue();
		assertThat(body).doesNotContain(hash);
	}

	@Test
	void storedHashMatchesMultibytePasswordOf72Bytes() {
		String password = "é".repeat(35) + "a1";
		String email = uniqueEmail();
		String phone = uniquePhone();

		ApiResponse response = post(email, password, "Ada", "Lovelace", phone);

		assertThat(response.status()).isEqualTo(201);
		String hash = jdbc.queryForObject(
				"select password_hash from app_user where email = ?",
				String.class,
				email);
		assertThat(passwordEncoder.matches(password, hash)).isTrue();
		assertThat(response.body()).doesNotContain(password).doesNotContain(hash);
	}

	@Test
	void duplicateEmailIgnoresCaseAndSpaces() {
		String email = uniqueEmail();
		post(email, "Motdepasse1", "Ada", "Lovelace", uniquePhone());

		ApiResponse response = post("  " + email.toUpperCase() + "  ", "AutreMot2", "Grace", "Hopper", uniquePhone());

		assertThat(response.status()).isEqualTo(409);
		assertThat(JsonPath.read(response.body(), "$.code").toString()).isEqualTo("EMAIL_ALREADY_USED");
		assertThat(response.body()).doesNotContain("AutreMot2");
		assertThat(countByEmail(email)).isEqualTo(1);
	}

	@Test
	void duplicatePhoneIgnoresSpacesAndHyphens() {
		String phone = "+3368%07d".formatted(PHONE_SEQUENCE.incrementAndGet());
		post(uniqueEmail(), "Motdepasse1", "Ada", "Lovelace", phone);
		String digits = phone.substring(1);
		String spaced = "+" + digits.charAt(0) + digits.charAt(1) + " " + digits.substring(2, 4) + "-"
				+ digits.substring(4, 6) + "-" + digits.substring(6);

		ApiResponse response = post(uniqueEmail(), "AutreMot2", "Grace", "Hopper", spaced);

		assertThat(response.status()).isEqualTo(409);
		assertThat(JsonPath.read(response.body(), "$.code").toString()).isEqualTo("DUPLICATE_PHONE");
		assertThat(countByPhone(phone)).isEqualTo(1);
	}

	@Test
	void invalidFieldsReturnValidationError() {
		String body = """
				{"email":"pas-un-email","password":"court","firstName":" ","lastName":"%s","phone":"12345"}
				""".formatted("N".repeat(101));

		ApiResponse response = postRaw(body);

		assertThat(response.status()).isEqualTo(400);
		assertThat(JsonPath.read(response.body(), "$.code").toString()).isEqualTo("VALIDATION_ERROR");
		List<String> fields = JsonPath.read(response.body(), "$.errors[*].field");
		assertThat(fields).containsExactlyInAnyOrder("email", "password", "firstName", "lastName", "phone");
		assertThat(response.body()).doesNotContain("court");
	}

	@Test
	void malformedJsonReturnsMalformedRequest() {
		String secret = "MalformeSecret9";

		ApiResponse response = postRaw("{\"password\":\"" + secret + "\"");

		assertThat(response.status()).isEqualTo(400);
		assertThat(JsonPath.read(response.body(), "$.code").toString()).isEqualTo("MALFORMED_REQUEST");
		assertThat(response.body()).doesNotContain(secret);
		assertThat(response.body()).doesNotContain("Exception");
	}

	@Test
	void databaseCheckRejectsUppercaseEmail() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO app_user (
				    id, email, password_hash, first_name, last_name, phone, status,
				    failed_login_attempts, version, created_at, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 0, 0, now(), now())
				""",
				UUID.randomUUID(),
				"BAD@EXAMPLE.COM",
				"x",
				"Ada",
				"Lovelace",
				uniquePhone()))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void postgresqlUniqueConstraintNamesMatchTheTranslator() {
		List<String> catalogNames = jdbc.queryForList(
				"select conname from pg_constraint where conrelid = 'app_user'::regclass and contype = 'u'",
				String.class);
		assertThat(catalogNames).containsExactlyInAnyOrder(
				RegistrationService.EMAIL_UNIQUE_CONSTRAINT,
				RegistrationService.PHONE_UNIQUE_CONSTRAINT);

		String email = uniqueEmail();
		String phone = uniquePhone();
		post(email, "Motdepasse1", "Ada", "Lovelace", phone);

		DataIntegrityViolationException emailViolation = catchViolation(() -> userRepository.saveAndFlush(
				AppUser.register(UUID.randomUUID(), email, "hash", "Ada", "Lovelace", uniquePhone(), clock.instant())));
		DataIntegrityViolationException phoneViolation = catchViolation(() -> userRepository.saveAndFlush(
				AppUser.register(UUID.randomUUID(), uniqueEmail(), "hash", "Ada", "Lovelace", phone, clock.instant())));

		assertThat(DataIntegrityConstraints.name(emailViolation)).isEqualTo(RegistrationService.EMAIL_UNIQUE_CONSTRAINT);
		assertThat(DataIntegrityConstraints.name(phoneViolation)).isEqualTo(RegistrationService.PHONE_UNIQUE_CONSTRAINT);
	}

	@Test
	void twentyConcurrentRegistrationsWithTheSameEmailYieldASingleRow() throws Exception {
		String email = uniqueEmail();
		String password = "Course1234";
		int threads = 20;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ApiResponse>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				String phone = uniquePhone();
				futures.add(pool.submit(() -> {
					ready.countDown();
					if (!start.await(30, TimeUnit.SECONDS)) {
						throw new IllegalStateException("départ non reçu");
					}
					return post(email, password, "Ada", "Lovelace", phone);
				}));
			}
			assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			int created = 0;
			int conflicts = 0;
			for (Future<ApiResponse> future : futures) {
				ApiResponse response = future.get(120, TimeUnit.SECONDS);
				if (response.status() == 201) {
					created++;
					assertThat(response.body()).doesNotContain(password);
				}
				else if (response.status() == 409) {
					conflicts++;
					assertThat(JsonPath.read(response.body(), "$.code").toString()).isEqualTo("EMAIL_ALREADY_USED");
				}
				else {
					throw new AssertionError("Statut inattendu " + response.status() + " : " + response.body());
				}
			}
			assertThat(created).isEqualTo(1);
			assertThat(conflicts).isEqualTo(19);
			assertThat(countByEmail(email)).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void logsDoNotContainPasswordOrHash(CapturedOutput output) {
		String password = "Nt7-cascade-phrase";
		String email = uniqueEmail();
		ApiResponse created = post(email, password, "Ada", "Lovelace", uniquePhone());
		assertThat(created.status()).isEqualTo(201);
		String hash = jdbc.queryForObject(
				"select password_hash from app_user where email = ?",
				String.class,
				email);
		post("  " + email.toUpperCase() + " ", password, "Ada", "Lovelace", uniquePhone());

		assertThat(output.getAll()).doesNotContain(password);
		assertThat(output.getAll()).doesNotContain(hash);
		assertThat(output.getAll()).doesNotContain("$2a$12$");
		assertThat(output.getAll()).doesNotContain("$2b$12$");
	}

	private ApiResponse post(String email, String password, String firstName, String lastName, String phone) {
		return postRaw("""
				{"email":%s,"password":%s,"firstName":%s,"lastName":%s,"phone":%s}
				""".formatted(quote(email), quote(password), quote(firstName), quote(lastName), quote(phone)));
	}

	private ApiResponse postRaw(String json) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/auth/register"))
				.header("Content-Type", "application/json; charset=UTF-8")
				.header("Accept", "application/json, application/problem+json")
				.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return new ApiResponse(
					response.statusCode(),
					response.body(),
					response.headers().firstValue("location").orElse(null));
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private record ApiResponse(int status, String body, String location) {
	}

	private static String quote(String value) {
		return "\"" + value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				+ "\"";
	}

	private int countByEmail(String email) {
		Integer count = jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class, email);
		return count == null ? 0 : count;
	}

	private int countByPhone(String phone) {
		Integer count = jdbc.queryForObject("select count(*) from app_user where phone = ?", Integer.class, phone);
		return count == null ? 0 : count;
	}

	private static String uniqueEmail() {
		return "user." + UUID.randomUUID() + "@example.com";
	}

	private static String uniquePhone() {
		return "+3361%07d".formatted(PHONE_SEQUENCE.incrementAndGet());
	}

	private static Path createKeysDir() {
		try {
			return Files.createTempDirectory("user-service-register-keys");
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static DataIntegrityViolationException catchViolation(Runnable action) {
		try {
			action.run();
		}
		catch (DataIntegrityViolationException exception) {
			return exception;
		}
		throw new AssertionError("Violation d'unicité attendue");
	}

}

package com.mywallet.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.mywallet.user.config.AppProperties;
import com.mywallet.user.config.JwtKeyStore;
import com.mywallet.user.entity.AppUser;
import com.mywallet.user.exception.InvalidAccessTokenException;
import com.mywallet.user.service.AccessTokenClaims;
import com.mywallet.user.service.IssuedAccessToken;
import com.mywallet.user.service.JwtService;
import com.mywallet.user.support.MutableClock;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.hikari.maximum-pool-size=30"
})
@Import(LoginAndJwtIntegrationTest.ClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LoginAndJwtIntegrationTest {

	private static final Instant START = Instant.parse("2026-06-01T00:00:00Z");

	private static final String PASSWORD = "Motdepasse1";

	private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(3_000_000);

	private static final Path KEYS_DIR = createKeysDir();

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@DynamicPropertySource
	static void jwtKeys(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.keys-dir", KEYS_DIR::toString);
	}

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	@LocalServerPort
	private int port;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JwtKeyStore jwtKeyStore;

	@Autowired
	private AppProperties appProperties;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoSpyBean
	private BCryptPasswordEncoder passwordEncoder;

	@BeforeEach
	void resetClock() {
		clock.set(START);
	}

	@Test
	void issuedTokenContainsExpectedClaimsAndExactLifetime() throws Exception {
		AppUser user = sampleUser();
		IssuedAccessToken first = jwtService.issueAccessToken(user);
		IssuedAccessToken second = jwtService.issueAccessToken(user);

		assertThat(first.expiresInSeconds()).isEqualTo(900);
		AccessTokenClaims claims = jwtService.verify(first.token());
		assertThat(claims.subject()).isEqualTo(user.getId());
		assertThat(claims.issuer()).isEqualTo("http://localhost:8081");
		assertThat(claims.issuedAt()).isEqualTo(START);
		assertThat(claims.expiresAt()).isEqualTo(START.plus(Duration.ofMinutes(15)));
		assertThat(claims.email()).isEqualTo(user.getEmail());
		assertThat(claims.roles()).containsExactly("USER");
		assertThat(claims.keyId()).isEqualTo(jwtKeyStore.keyId());
		assertThat(claims.tokenId()).isNotEqualTo(jwtService.verify(second.token()).tokenId());

		SignedJWT parsed = SignedJWT.parse(first.token());
		assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
		assertThat(parsed.getHeader().getKeyID()).isEqualTo(jwtKeyStore.keyId());
		assertThat(parsed.getJWTClaimsSet().getClaims().keySet())
				.containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti", "email", "roles");
		String serialized = first.token();
		assertThat(serialized).doesNotContain(user.getFirstName());
		assertThat(serialized).doesNotContain(user.getLastName());
		assertThat(serialized).doesNotContain(user.getPhone());
		assertThat(serialized).doesNotContain(user.getPasswordHash());

		RSAKey fromJwks = publicKeyFrom(jwtService.jsonWebKeySet());
		assertThat(parsed.verify(new RSASSAVerifier(fromJwks))).isTrue();
	}

	@Test
	void verifyRejectsExpiredTamperedAndForgedTokens() throws Exception {
		AppUser user = sampleUser();
		String token = jwtService.issueAccessToken(user).token();
		assertThat(jwtService.verify(token).subject()).isEqualTo(user.getId());

		clock.advance(Duration.ofMinutes(16));
		assertRejected(token);
		clock.set(START);

		assertRejected(flip(token, 1));
		assertRejected(flip(token, 2));
		assertRejected(new PlainJWT(validClaims(user, appProperties.jwt().issuer())).serialize());
		assertRejected(sign(validClaims(user, appProperties.jwt().issuer()),
				new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(jwtKeyStore.keyId()).build(),
				new MACSigner(jwtKeyStore.publicKey().getEncoded())));

		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair foreign = generator.generateKeyPair();
		assertRejected(sign(validClaims(user, appProperties.jwt().issuer()),
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwtKeyStore.keyId()).build(),
				new RSASSASigner(foreign.getPrivate())));
		assertRejected(sign(validClaims(user, "http://evil.example"),
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwtKeyStore.keyId()).build(),
				new RSASSASigner(jwtKeyStore.privateKey())));
		assertRejected(sign(validClaims(user, appProperties.jwt().issuer()),
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("unknown-kid").build(),
				new RSASSASigner(jwtKeyStore.privateKey())));
	}

	@Test
	void jwksPublishesOnlyThePublicKeyAndReloadsTheSameKid() throws Exception {
		String token = jwtService.issueAccessToken(sampleUser()).token();
		HttpResponse<String> response = httpClient.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/.well-known/jwks.json")).GET().build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("public, max-age=300");
		Number keyCount = JsonPath.read(response.body(), "$.keys.length()");
		assertThat(keyCount.intValue()).isEqualTo(1);
		Map<String, Object> key = JsonPath.read(response.body(), "$.keys[0]");
		assertThat(key.keySet()).containsExactlyInAnyOrder("kty", "kid", "use", "alg", "n", "e");
		assertThat(key.get("kty")).isEqualTo("RSA");
		assertThat(key.get("alg")).isEqualTo("RS256");
		assertThat(key.get("use")).isEqualTo("sig");
		assertThat(key.get("kid")).isEqualTo(jwtKeyStore.keyId());
		assertThat(key.get("kid")).isEqualTo(SignedJWT.parse(token).getHeader().getKeyID());
		assertThat(response.body()).doesNotContain("\"d\"");

		byte[] privatePem = Files.readAllBytes(KEYS_DIR.resolve("jwt-private.pem"));
		JwtKeyStore reloaded = new JwtKeyStore(appProperties);
		assertThat(reloaded.keyId()).isEqualTo(jwtKeyStore.keyId());
		assertThat(reloaded.modulus()).isEqualTo(jwtKeyStore.modulus());
		assertThat(Files.readAllBytes(KEYS_DIR.resolve("jwt-private.pem"))).isEqualTo(privatePem);
	}

	@Test
	void loginReturnsAValidTokenForTheRegisteredUser() {
		String email = uniqueEmail();
		assertThat(register(email, PASSWORD).status()).isEqualTo(201);

		ApiResponse login = login(email, PASSWORD);
		assertThat(login.status()).isEqualTo(200);
		String tokenType = JsonPath.read(login.body(), "$.tokenType");
		Number expiresIn = JsonPath.read(login.body(), "$.expiresIn");
		assertThat(tokenType).isEqualTo("Bearer");
		assertThat(expiresIn.intValue()).isEqualTo(900);
		String token = JsonPath.read(login.body(), "$.accessToken");
		UUID id = jdbc.queryForObject("select id from app_user where email = ?", UUID.class, email);
		assertThat(jwtService.verify(token).subject()).isEqualTo(id);
		assertThat(jwtService.verify(token).email()).isEqualTo(email);
	}

	@Test
	void failureResponsesAreByteIdentical() {
		String email = uniqueEmail();
		assertThat(register(email, PASSWORD).status()).isEqualTo(201);
		ApiResponse wrongPassword = login(email, "MauvaisMot2");
		ApiResponse unknownEmail = login("inconnu." + UUID.randomUUID() + "@example.com", "MauvaisMot2");
		ApiResponse shortPassword = login("court." + UUID.randomUUID() + "@example.com", "x");
		jdbc.update(
				"update app_user set failed_login_attempts = 5, locked_until = ? where email = ?",
				java.sql.Timestamp.from(clock.instant().plus(Duration.ofHours(1))),
				email);
		ApiResponse locked = login(email, PASSWORD);

		assertThat(wrongPassword.status()).isEqualTo(401);
		assertThat(unknownEmail.body()).isEqualTo(wrongPassword.body());
		assertThat(locked.body()).isEqualTo(wrongPassword.body());
		assertThat(shortPassword.status()).isEqualTo(401);
		assertThat(shortPassword.body()).isEqualTo(unknownEmail.body());
		assertThat(wrongPassword.body()).contains("INVALID_CREDENTIALS");
		assertThat(wrongPassword.body()).doesNotContain(email);
		assertThat(wrongPassword.body()).doesNotContain(PASSWORD);
		assertThat(attempts(email)).isEqualTo(5);
	}

	@Test
	void lockoutExpiresAndTheCounterRestarts() {
		String email = uniqueEmail();
		assertThat(register(email, PASSWORD).status()).isEqualTo(201);
		long version = version(email);
		Object updatedAt = updatedAt(email);

		for (int attempt = 0; attempt < 5; attempt++) {
			assertThat(login(email, "MauvaisMot2").status()).isEqualTo(401);
		}
		assertThat(attempts(email)).isEqualTo(5);
		assertThat(lockedUntil(email)).isNotNull();
		assertThat(login(email, "EncoreFaux1").status()).isEqualTo(401);
		assertThat(attempts(email)).isEqualTo(5);
		assertThat(login(email, PASSWORD).status()).isEqualTo(401);
		assertThat(attempts(email)).isEqualTo(5);

		clock.advance(Duration.ofMinutes(15));
		ApiResponse recovered = login(email, PASSWORD);
		assertThat(recovered.status()).isEqualTo(200);
		assertThat(attempts(email)).isZero();
		assertThat(lockedUntil(email)).isNull();

		assertThat(login(email, "MauvaisMot2").status()).isEqualTo(401);
		assertThat(attempts(email)).isEqualTo(1);
		assertThat(lockedUntil(email)).isNull();
		assertThat(version(email)).isEqualTo(version);
		assertThat(updatedAt(email)).isEqualTo(updatedAt);
	}

	@Test
	void suspendedAccountDependsOnThePassword() {
		String email = uniqueEmail();
		assertThat(register(email, PASSWORD).status()).isEqualTo(201);
		jdbc.update("update app_user set status = 'SUSPENDED' where email = ?", email);

		ApiResponse correct = login(email, PASSWORD);
		assertThat(correct.status()).isEqualTo(403);
		String code = JsonPath.read(correct.body(), "$.code");
		assertThat(code).isEqualTo("USER_SUSPENDED");
		assertThat(attempts(email)).isZero();

		ApiResponse wrong = login(email, "MauvaisMot2");
		ApiResponse unknown = login("inconnu." + UUID.randomUUID() + "@example.com", "MauvaisMot2");
		assertThat(wrong.status()).isEqualTo(401);
		assertThat(wrong.body()).isEqualTo(unknown.body());
		assertThat(attempts(email)).isEqualTo(1);
	}

	@Test
	void loginNormalizesEmail() {
		String email = uniqueEmail();
		assertThat(register(email, PASSWORD).status()).isEqualTo(201);
		ApiResponse login = login("  " + email.toUpperCase() + "  ", PASSWORD);
		assertThat(login.status()).isEqualTo(200);
		String token = JsonPath.read(login.body(), "$.accessToken");
		assertThat(jwtService.verify(token).email()).isEqualTo(email);
	}

	@Test
	void concurrentFailuresAreAllCountedAndLockTheAccount() throws Exception {
		String email = uniqueEmail();
		assertThat(register(email, PASSWORD).status()).isEqualTo(201);
		List<ApiResponse> responses = burst(10, loginJson(email, "MauvaisMot2"));
		assertThat(responses).allSatisfy(response -> assertThat(response.status()).isEqualTo(401));
		assertThat(attempts(email)).isEqualTo(10);
		assertThat(lockedUntil(email)).isNotNull();
	}

	@Test
	void concurrentSuccessesAllSucceedAndResetTheCounter() throws Exception {
		String email = uniqueEmail();
		assertThat(register(email, PASSWORD).status()).isEqualTo(201);
		jdbc.update("update app_user set failed_login_attempts = 3 where email = ?", email);
		List<ApiResponse> responses = burst(20, loginJson(email, PASSWORD));
		assertThat(responses).allSatisfy(response -> assertThat(response.status()).isEqualTo(200));
		assertThat(attempts(email)).isZero();
		assertThat(lockedUntil(email)).isNull();
	}

	@Test
	void unknownEmailStillRunsBcrypt() {
		clearInvocations(passwordEncoder);
		ApiResponse response = login("inconnu." + UUID.randomUUID() + "@example.com", PASSWORD);
		assertThat(response.status()).isEqualTo(401);
		verify(passwordEncoder, times(1)).matches(any(CharSequence.class), anyString());
	}

	@Test
	void logsDoNotContainPasswordHashOrToken(CapturedOutput output) {
		String password = "Nt7-login-phrase";
		String wrong = "Wrong-phrase-88";
		String email = uniqueEmail();
		assertThat(register(email, password).status()).isEqualTo(201);
		ApiResponse success = login(email, password);
		assertThat(success.status()).isEqualTo(200);
		String token = JsonPath.read(success.body(), "$.accessToken");
		String hash = jdbc.queryForObject("select password_hash from app_user where email = ?", String.class, email);
		for (int attempt = 0; attempt < 5; attempt++) {
			assertThat(login(email, wrong).status()).isEqualTo(401);
		}
		assertThat(login(email, password).status()).isEqualTo(401);

		String logs = output.getAll();
		assertThat(logs).doesNotContain(password);
		assertThat(logs).doesNotContain(wrong);
		assertThat(logs).doesNotContain(hash);
		assertThat(logs).doesNotContain(token);
		assertThat(logs).doesNotContain("$2a$12$");
		assertThat(logs).doesNotContain("$2b$12$");
		assertThat(logs).doesNotContain("user-service-dummy-login-hash");
	}

	private void assertRejected(String token) {
		assertThatThrownBy(() -> jwtService.verify(token)).isInstanceOf(InvalidAccessTokenException.class);
	}

	private JWTClaimsSet validClaims(AppUser user, String issuer) {
		Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		return new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject(user.getId().toString())
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(issuedAt.plus(Duration.ofMinutes(15))))
				.jwtID(UUID.randomUUID().toString())
				.claim("email", user.getEmail())
				.claim("roles", List.of("USER"))
				.build();
	}

	private static String sign(JWTClaimsSet claims, JWSHeader header, com.nimbusds.jose.JWSSigner signer) throws Exception {
		SignedJWT jwt = new SignedJWT(header, claims);
		jwt.sign(signer);
		return jwt.serialize();
	}

	private static String flip(String token, int part) {
		String[] parts = token.split("\\.");
		char[] chars = parts[part].toCharArray();
		chars[0] = chars[0] == 'A' ? 'B' : 'A';
		parts[part] = new String(chars);
		return String.join(".", parts);
	}

	private static RSAKey publicKeyFrom(Map<String, Object> jwks) throws Exception {
		@SuppressWarnings("unchecked")
		Map<String, Object> key = ((List<Map<String, Object>>) jwks.get("keys")).get(0);
		return new RSAKey.Builder(new Base64URL((String) key.get("n")), new Base64URL((String) key.get("e"))).build();
	}

	private AppUser sampleUser() {
		return AppUser.register(
				UUID.randomUUID(),
				"ada." + UUID.randomUUID() + "@example.com",
				"hash-qui-ne-doit-pas-sortir",
				"PrenomSecret",
				"NomSecret",
				"+33619998877",
				clock.instant());
	}

	private ApiResponse register(String email, String password) {
		return send("POST", "/api/auth/register", """
				{"email":%s,"password":%s,"firstName":"Ada","lastName":"Lovelace","phone":%s}
				""".formatted(quote(email), quote(password), quote(uniquePhone())));
	}

	private ApiResponse login(String email, String password) {
		return send("POST", "/api/auth/login", loginJson(email, password));
	}

	private static String loginJson(String email, String password) {
		return "{\"email\":%s,\"password\":%s}".formatted(quote(email), quote(password));
	}

	private ApiResponse send(String method, String path, String json) {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json; charset=UTF-8")
				.header("Accept", "application/json, application/problem+json")
				.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
				.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return new ApiResponse(response.statusCode(), response.body());
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private List<ApiResponse> burst(int count, String json) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(count);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<ApiResponse>> futures = new ArrayList<>();
			for (int index = 0; index < count; index++) {
				futures.add(pool.submit(() -> {
					start.await();
					return send("POST", "/api/auth/login", json);
				}));
			}
			start.countDown();
			List<ApiResponse> responses = new ArrayList<>();
			for (Future<ApiResponse> future : futures) {
				responses.add(future.get(2, TimeUnit.MINUTES));
			}
			return responses;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private int attempts(String email) {
		Integer count = jdbc.queryForObject(
				"select failed_login_attempts from app_user where email = ?",
				Integer.class,
				email);
		return count == null ? 0 : count;
	}

	private Object lockedUntil(String email) {
		return jdbc.queryForObject("select locked_until from app_user where email = ?", Object.class, email);
	}

	private long version(String email) {
		Long version = jdbc.queryForObject("select version from app_user where email = ?", Long.class, email);
		return version == null ? -1 : version;
	}

	private Object updatedAt(String email) {
		return jdbc.queryForObject("select updated_at from app_user where email = ?", Object.class, email);
	}

	private static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String uniqueEmail() {
		return "user." + UUID.randomUUID() + "@example.com";
	}

	private static String uniquePhone() {
		return "+3362%07d".formatted(PHONE_SEQUENCE.incrementAndGet());
	}

	private static Path createKeysDir() {
		try {
			return Files.createTempDirectory("user-service-login-keys");
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private record ApiResponse(int status, String body) {
	}

	@TestConfiguration
	static class ClockConfiguration {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(START);
		}

	}

}

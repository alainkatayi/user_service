package com.mywallet.user.config;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtDecoder jwtDecoder,
			SecurityProblemResponses problemResponses) throws Exception {
		http
				// API à jeton Bearer sans cookie.
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(problemResponses)
						.accessDeniedHandler(problemResponses))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
						.requestMatchers(HttpMethod.GET, "/.well-known/jwks.json", "/actuator/health").permitAll()
						.requestMatchers(HttpMethod.GET, "/internal/users/by-phone").permitAll()
						.requestMatchers(HttpMethod.GET, "/internal/users/*").permitAll()
						.requestMatchers("/api/users/**").authenticated()
						.requestMatchers("/internal/**").denyAll()
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(problemResponses)
						.accessDeniedHandler(problemResponses)
						.jwt(jwt -> jwt
								.decoder(jwtDecoder)
								.jwtAuthenticationConverter(accessTokenAuthentication())));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtKeyStore keyStore, AppProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyStore.publicKey())
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
		timestamps.setClock(clock);
		OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
				timestamps,
				new JwtIssuerValidator(properties.jwt().issuer()),
				keyIdValidator(keyStore));
		decoder.setJwtValidator(validator);
		return decoder;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(
				"http://localhost:4200",
				"http://127.0.0.1:4200"));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	private static Converter<Jwt, AbstractAuthenticationToken> accessTokenAuthentication() {
		return jwt -> {
			String subject = jwt.getSubject();
			try {
				UUID.fromString(subject);
			}
			catch (IllegalArgumentException | NullPointerException exception) {
				throw new InvalidBearerTokenException("Jeton d'accès invalide.");
			}
			return new JwtAuthenticationToken(jwt, roles(jwt), subject);
		};
	}

	private static OAuth2TokenValidator<Jwt> keyIdValidator(JwtKeyStore keyStore) {
		return jwt -> keyStore.keyId().equals(jwt.getHeaders().get("kid"))
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
	}

	private static Collection<GrantedAuthority> roles(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList("roles");
		if (roles == null || roles.isEmpty()) {
			return List.of();
		}
		return roles.stream()
				.map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
				.map(SimpleGrantedAuthority::new)
				.map(GrantedAuthority.class::cast)
				.toList();
	}

}

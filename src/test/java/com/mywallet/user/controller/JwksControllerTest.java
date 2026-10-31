package com.mywallet.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mywallet.user.service.JwtService;

@WebMvcTest(JwksController.class)
class JwksControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void publishesThePublicKeySet() throws Exception {
		when(jwtService.jsonWebKeySet()).thenReturn(Map.of("keys", List.of(Map.of(
				"kty", "RSA",
				"kid", "kid-test",
				"use", "sig",
				"alg", "RS256",
				"n", "modulus",
				"e", "AQAB"))));

		mockMvc.perform(get("/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "public, max-age=300"))
				.andExpect(jsonPath("$.keys.length()").value(1))
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].kid").value("kid-test"))
				.andExpect(jsonPath("$.keys[0].alg").value("RS256"))
				.andExpect(content().string(not(containsString("\"d\""))));
	}

	@Test
	void unexpectedExceptionDoesNotLeak() throws Exception {
		when(jwtService.jsonWebKeySet()).thenThrow(new IllegalStateException("private key d=LeakMe123"));

		mockMvc.perform(get("/.well-known/jwks.json"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(content().string(not(containsString("LeakMe123"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))));
	}

}

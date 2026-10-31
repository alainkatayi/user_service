package com.mywallet.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AppUserTest {

	@Test
	void registerStartsAnActiveAccountAtTheGivenInstant() {
		UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
		Instant now = Instant.parse("2026-10-31T12:00:00Z");

		AppUser user = AppUser.register(id, "ada@example.com", "hash", "Ada", "Lovelace", "+33612345678", now);

		assertThat(user.getId()).isEqualTo(id);
		assertThat(user.getEmail()).isEqualTo("ada@example.com");
		assertThat(user.getPasswordHash()).isEqualTo("hash");
		assertThat(user.getFirstName()).isEqualTo("Ada");
		assertThat(user.getLastName()).isEqualTo("Lovelace");
		assertThat(user.getPhone()).isEqualTo("+33612345678");
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getFailedLoginAttempts()).isZero();
		assertThat(user.getLockedUntil()).isNull();
		assertThat(user.getCreatedAt()).isEqualTo(now);
		assertThat(user.getUpdatedAt()).isEqualTo(now);
		assertThat(user.isNew()).isTrue();
	}

}

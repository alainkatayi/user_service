package com.mywallet.user.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Identifiant assigné : Persistable évite un merge qui n'insère rien.
 * Seul le hash du jeton est stocké, jamais le jeton en clair.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken implements Persistable<UUID> {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, length = 64, updatable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Transient
	private boolean newEntity;

	protected RefreshToken() {
		this.newEntity = false;
	}

	private RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.revokedAt = null;
		this.createdAt = createdAt;
		this.newEntity = true;
	}

	public static RefreshToken issue(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant createdAt) {
		return new RefreshToken(id, userId, tokenHash, expiresAt, createdAt);
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return newEntity;
	}

	@PostPersist
	@PostLoad
	void markPersisted() {
		this.newEntity = false;
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

}

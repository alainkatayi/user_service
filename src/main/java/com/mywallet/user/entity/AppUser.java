package com.mywallet.user.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

/**
 * Un identifiant assigné fait prendre l'entité pour une ligne déjà existante : Spring Data appelle alors merge, et avec @Version cet update n'insère rien.
 * AppUser implémente Persistable avec un drapeau newEntity, vrai seulement dans register et remis à faux par @PostPersist et @PostLoad, pour que save exécute persist.
 * DynamicUpdate limite une sauvegarde de profil aux colonnes sales, pour ne pas réécraser failed_login_attempts ou locked_until.
 */
@DynamicUpdate
@Entity
@Table(name = "app_user")
public class AppUser implements Persistable<UUID> {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "email", nullable = false, length = 150)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "phone", nullable = false, length = 20)
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private UserStatus status;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Transient
	private boolean newEntity;

	protected AppUser() {
		this.newEntity = false;
	}

	private AppUser(
			UUID id,
			String email,
			String passwordHash,
			String firstName,
			String lastName,
			String phone,
			Instant now) {
		this.id = id;
		this.email = email;
		this.passwordHash = passwordHash;
		this.firstName = firstName;
		this.lastName = lastName;
		this.phone = phone;
		this.status = UserStatus.ACTIVE;
		this.failedLoginAttempts = 0;
		this.lockedUntil = null;
		this.createdAt = now;
		this.updatedAt = now;
		this.newEntity = true;
	}

	public void updateProfile(String firstName, String lastName, String phone, Instant now) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.phone = phone;
		this.updatedAt = now;
	}

	public void changePassword(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.updatedAt = now;
	}

	public static AppUser register(
			UUID id,
			String email,
			String passwordHash,
			String firstName,
			String lastName,
			String phone,
			Instant now) {
		return new AppUser(id, email, passwordHash, firstName, lastName, phone, now);
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
	void markNotNew() {
		this.newEntity = false;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getPhone() {
		return phone;
	}

	public UserStatus getStatus() {
		return status;
	}

	public int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

package com.mywallet.user.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.mywallet.user.entity.AppUser;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);

	/**
	 * Incrémente le compteur en une seule requête, pour ne pas perdre d'échecs concurrents.
	 * Si le verrouillage est déjà expiré, le compteur repart à 1. La requête ne touche ni
	 * version ni updated_at : ce n'est pas une modification de profil, donc elle ne fait pas
	 * échouer le verrouillage optimiste d'une édition de profil en cours. DynamicUpdate sur
	 * AppUser empêche cette édition de réécrire un compteur périmé.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query(value = """
			UPDATE app_user
			SET failed_login_attempts = CASE
			        WHEN locked_until IS NOT NULL AND locked_until <= CAST(:now AS timestamptz) THEN 1
			        ELSE failed_login_attempts + 1
			    END,
			    locked_until = CASE
			        WHEN (
			            CASE
			                WHEN locked_until IS NOT NULL AND locked_until <= CAST(:now AS timestamptz) THEN 1
			                ELSE failed_login_attempts + 1
			            END
			        ) >= :maxAttempts THEN CAST(:lockUntil AS timestamptz)
			        ELSE NULL
			    END
			WHERE id = :id
			""", nativeQuery = true)
	int registerFailedLogin(
			@Param("id") UUID id,
			@Param("now") Instant now,
			@Param("maxAttempts") int maxAttempts,
			@Param("lockUntil") Instant lockUntil);

	/**
	 * Remet le compteur à zéro sans modifier version ni updated_at.
	 * Ne fait rien si un verrouillage est encore dans le futur.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query(value = """
			UPDATE app_user
			SET failed_login_attempts = 0,
			    locked_until = NULL
			WHERE id = :id
			  AND (locked_until IS NULL OR locked_until <= CAST(:now AS timestamptz))
			""", nativeQuery = true)
	int resetLoginFailures(@Param("id") UUID id, @Param("now") Instant now);

}

package com.mywallet.user.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.mywallet.user.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * Révoque le jeton seulement s'il est encore actif. Zéro ligne : un autre appel a gagné,
	 * ou le jeton a expiré. Ne révoque pas les autres sessions.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query(value = """
			UPDATE refresh_token
			SET revoked_at = CAST(:now AS timestamptz)
			WHERE token_hash = :tokenHash
			  AND revoked_at IS NULL
			  AND expires_at > CAST(:now AS timestamptz)
			""", nativeQuery = true)
	int revokeIfActive(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

	/**
	 * Coupe toutes les sessions encore actives de l'utilisateur (rejeu d'un jeton déjà révoqué).
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query(value = """
			UPDATE refresh_token
			SET revoked_at = CAST(:now AS timestamptz)
			WHERE user_id = :userId
			  AND revoked_at IS NULL
			""", nativeQuery = true)
	int revokeAllActive(@Param("userId") UUID userId, @Param("now") Instant now);

	/**
	 * Déconnexion idempotente : aucune ligne modifiée si le jeton est inconnu ou déjà révoqué.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query(value = """
			UPDATE refresh_token
			SET revoked_at = CAST(:now AS timestamptz)
			WHERE token_hash = :tokenHash
			  AND revoked_at IS NULL
			""", nativeQuery = true)
	int revoke(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

}

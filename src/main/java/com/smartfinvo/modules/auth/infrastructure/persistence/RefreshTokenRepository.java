package com.smartfinvo.modules.auth.infrastructure.persistence;

import com.smartfinvo.modules.auth.domain.RefreshToken;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository
        extends ReactiveCrudRepository<RefreshToken, UUID> {

    // Find token by its hash — the hot path on every refresh request
    // After checking Redis first, this is the fallback
    // SELECT * FROM refresh_token WHERE token_hash = ?
    Mono<RefreshToken> findByTokenHash(String tokenHash);

    // Find all active tokens for a user
    // Used in "active sessions" screen
    // SELECT * FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL
    Flux<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    // Find all tokens in a family
    // Used for reuse detection — when reuse is detected,
    // we load all tokens in the family and revoke them all
    // SELECT * FROM refresh_token WHERE family = ?
    Flux<RefreshToken> findByFamily(UUID family);

    // Revoke all tokens for a user — used on logout all devices
    // @Modifying tells Spring this query changes data (not a SELECT)
    // @Query lets us write custom SQL when method name is not enough
    @Modifying
    @Query("UPDATE refresh_token SET revoked_at = :revokedAt, " +
           "revoke_reason = :reason " +
           "WHERE user_id = :userId AND revoked_at IS NULL")
    Mono<Integer> revokeAllByUserId(UUID userId, Instant revokedAt, String reason);

    // Revoke all tokens in a family — used when reuse is detected
    @Modifying
    @Query("UPDATE refresh_token SET revoked_at = :revokedAt, " +
           "revoke_reason = :reason " +
           "WHERE family = :family AND revoked_at IS NULL")
    Mono<Integer> revokeAllByFamily(UUID family, Instant revokedAt, String reason);

    // Cleanup job — deletes expired and revoked tokens older than 30 days
    // Keeps the table small — called by a scheduled job nightly
    @Modifying
    @Query("DELETE FROM refresh_token " +
           "WHERE expires_at < :cutoff OR revoked_at < :cutoff")
    Mono<Integer> deleteExpiredBefore(Instant cutoff);
}
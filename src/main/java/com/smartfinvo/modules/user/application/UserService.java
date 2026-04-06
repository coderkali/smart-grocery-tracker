package com.smartfinvo.modules.user.application;

import com.smartfinvo.modules.auth.infrastructure.cache.RefreshTokenCacheRepository;
import com.smartfinvo.modules.auth.infrastructure.persistence.RefreshTokenRepository;
import com.smartfinvo.modules.auth.infrastructure.persistence.UserAccountRepository;
import com.smartfinvo.modules.user.infrastructure.web.dto.DeleteAccountRequest;
import com.smartfinvo.modules.user.infrastructure.web.dto.UpdateUserProfileRequest;
import com.smartfinvo.modules.user.infrastructure.web.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

// Handles all user profile operations
// Thin service — delegates data access to UserAccountRepository (owned by auth module)
// No @Transactional here — this method is read-only, no transaction needed
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    // All three repos live in the auth module — injected directly as Spring beans
    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCacheRepository refreshTokenCacheRepository;

    // ── GET /api/v1/users/me ──────────────────────────────────────────────
    // Fetches the full profile of the currently authenticated user
    // Returns Mono.error("USER_NOT_FOUND") if the userId from the JWT has no matching row —
    // this should not happen in normal operation but guards against deleted accounts
    public Mono<UserResponse> getCurrentUser(UUID userId) {
        log.debug("Fetching profile for userId={}", userId);

        return userAccountRepository
                .findById(userId)
                // Guard: JWT was valid but user row is missing (e.g. deleted between tokens)
                .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
                // Guard: Soft-deleted accounts — the JWT may still be valid after deletion
                .flatMap(user -> {
                    if (user.getDeletedAt() != null) {
                        log.warn("Profile fetch attempted on deleted account userId={}", userId);
                        return Mono.error(new RuntimeException("USER_NOT_FOUND"));
                    }
                    return Mono.just(toResponse(user));
                })
                .doOnSuccess(resp -> log.debug("Profile fetched userId={}", userId))
                .doOnError(err -> log.warn("Profile fetch failed userId={} reason={}", userId, err.getMessage()));
    }

    // ── PATCH /api/v1/users/me ────────────────────────────────────────────
    // Applies a partial update to the authenticated user's profile
    // Only non-null fields in the request are applied — omitted fields are left unchanged
    // Uses @Transactional so the load + save pair is atomic; R2DBC increments @Version automatically
    @Transactional("connectionFactoryTransactionManager")
    public Mono<UserResponse> updateProfile(UUID userId, UpdateUserProfileRequest request) {
        log.debug("Updating profile for userId={}", userId);

        return userAccountRepository
                .findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
                .flatMap(user -> {
                    if (user.getDeletedAt() != null) {
                        log.warn("Profile update attempted on deleted account userId={}", userId);
                        return Mono.error(new RuntimeException("USER_NOT_FOUND"));
                    }

                    // Apply only the fields the caller actually sent (null = leave unchanged)
                    if (request.getDisplayName() != null) {
                        user.setDisplayName(request.getDisplayName());
                    }
                    if (request.getFirstName() != null) {
                        user.setFirstName(request.getFirstName());
                    }
                    if (request.getLastName() != null) {
                        user.setLastName(request.getLastName());
                    }
                    if (request.getAvatarUrl() != null) {
                        user.setAvatarUrl(request.getAvatarUrl());
                    }

                    // @LastModifiedDate would handle this automatically, but setting explicitly
                    // mirrors the pattern used in ExpenseService and is more predictable
                    user.setUpdatedAt(Instant.now());

                    // save() with @Version on the entity triggers optimistic locking —
                    // R2DBC will throw OptimisticLockingFailureException on version conflict
                    return userAccountRepository.save(user);
                })
                .map(this::toResponse)
                .doOnSuccess(resp -> log.debug("Profile updated userId={}", userId))
                .doOnError(err -> log.warn("Profile update failed userId={} reason={}", userId, err.getMessage()));
    }

    // ── GET /api/v1/users/{userId} ────────────────────────────────────────
    // Admin-only: fetches another user's profile by their UUID
    // Two loads in sequence:
    //   1. Load the requesting user to verify they have isAdmin = true
    //   2. Load the target user and return their profile
    // Any missing/deleted account at either step returns the appropriate error
    public Mono<UserResponse> getUserById(UUID requestingUserId, UUID targetUserId) {
        log.debug("Admin profile lookup requestingUserId={} targetUserId={}", requestingUserId, targetUserId);

        return userAccountRepository
                .findById(requestingUserId)
                .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
                .flatMap(requestingUser -> {
                    // Reject soft-deleted accounts — their JWT may still be valid momentarily
                    if (requestingUser.getDeletedAt() != null) {
                        return Mono.error(new RuntimeException("USER_NOT_FOUND"));
                    }
                    // Only admins can look up other users
                    if (!Boolean.TRUE.equals(requestingUser.getIsAdmin())) {
                        log.warn("Non-admin user attempted admin lookup requestingUserId={}", requestingUserId);
                        return Mono.error(new RuntimeException("FORBIDDEN"));
                    }
                    // Requesting user is a verified admin — load the target
                    return userAccountRepository.findById(targetUserId);
                })
                .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
                .flatMap(targetUser -> {
                    if (targetUser.getDeletedAt() != null) {
                        log.warn("Admin lookup on deleted account targetUserId={}", targetUserId);
                        return Mono.error(new RuntimeException("USER_NOT_FOUND"));
                    }
                    return Mono.just(toResponse(targetUser));
                })
                .doOnSuccess(resp -> log.debug("Admin profile returned targetUserId={}", targetUserId))
                .doOnError(err -> log.warn("getUserById failed requestingUserId={} targetUserId={} reason={}",
                        requestingUserId, targetUserId, err.getMessage()));
    }

    // ── DELETE /api/v1/users/me ───────────────────────────────────────────
    // Soft-deletes the user and revokes all active sessions in one transaction.
    // Three steps always run together — if any step fails the whole thing rolls back:
    //   1. Soft-delete the UserAccount row (sets status=DELETED, deleted_at=now)
    //   2. Revoke all refresh tokens in PostgreSQL
    //   3. Purge all refresh token entries from the Redis cache
    // After this call the user's JWT may still be valid until it expires naturally
    // (access tokens are stateless) — the client should discard it immediately.
    @Transactional("connectionFactoryTransactionManager")
    public Mono<Void> deleteAccount(UUID userId, DeleteAccountRequest request) {
        log.info("Account deletion requested userId={} reason={}", userId, request.getReason());

        // Build the revoke reason string — used for audit trail on refresh_token rows
        String revokeReason = request.getReason() != null
                ? "ACCOUNT_DELETED: " + request.getReason()
                : "ACCOUNT_DELETED";

        return userAccountRepository
                .findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
                .flatMap(user -> {
                    if (user.getDeletedAt() != null) {
                        // Already deleted — treat as not found (idempotent-safe)
                        return Mono.error(new RuntimeException("USER_NOT_FOUND"));
                    }

                    // Step 1: soft-delete — sets status=DELETED and deleted_at=now()
                    user.softDelete();
                    return userAccountRepository.save(user);
                })
                .flatMap(savedUser ->
                        // Step 2: revoke every active refresh token in PostgreSQL
                        refreshTokenRepository
                                .revokeAllByUserId(userId, Instant.now(), revokeReason)
                                .doOnSuccess(count ->
                                        log.info("Revoked {} refresh tokens for deleted userId={}", count, userId))
                )
                .flatMap(revokedCount ->
                        // Step 3: purge the Redis token cache so stale entries don't linger
                        refreshTokenCacheRepository
                                .deleteAllByUserId(userId.toString())
                                .doOnSuccess(v ->
                                        log.info("Redis token cache cleared for deleted userId={}", userId))
                )
                .then()
                .doOnSuccess(v -> log.info("Account deleted successfully userId={}", userId))
                .doOnError(err -> log.error("Account deletion failed userId={} reason={}", userId, err.getMessage()));
    }

    // ── toResponse ────────────────────────────────────────────────────────
    // Maps UserAccount entity → UserResponse DTO
    // Kept private — callers always receive DTOs, never domain entities
    private UserResponse toResponse(com.smartfinvo.modules.auth.domain.UserAccount user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .emailVerified(user.getEmailVerified())
                .displayName(user.getDisplayName())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .onboardingStep(user.getOnboardingStep())
                .version(user.getVersion())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

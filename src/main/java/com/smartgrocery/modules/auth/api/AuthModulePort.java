package com.smartgrocery.modules.auth.api;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

// This is the internal contract for the auth module
// Every other module that needs auth functionality calls THIS interface
// Never calls the service directly
//
// Why this matters:
// When auth is extracted to a microservice — we write auth.proto
// matching these exact method signatures, generate gRPC stubs,
// and swap the implementation. Zero changes to callers.
public interface AuthModulePort {

    // Called by OAuth2 success handler after provider login
    Mono<TokenPairDto> processOAuth2Login(OAuth2LoginCommand command);

    // Called when client sends refresh token to get new access token
    Mono<TokenPairDto> refreshTokens(RefreshTokenCommand command);

    // Logout current device only
    Mono<Void> logout(LogoutCommand command);

    // Logout all devices — revokes all sessions for user
    Mono<Integer> logoutAll(LogoutAllCommand command);

    // Get current user info from JWT userId
    Mono<AuthUserDto> getCurrentUser(UUID userId);

    // Get all active sessions for a user
    Flux<SessionDto> getActiveSessions(UUID userId);

    // Revoke a specific session by sessionId
    Mono<Void> revokeSession(UUID userId, UUID sessionId);

    // ── Command objects ────────────────────────────────────────────────────
    // Commands carry the input data for each operation
    // Named clearly so they map directly to .proto message definitions later

    record OAuth2LoginCommand(
        String provider,        // "google" or "github"
        String providerId,      // unique ID from provider
        String email,           // email from provider
        String displayName,     // name from provider
        String avatarUrl,       // avatar from provider
        String ipAddress,       // for session tracking
        String deviceHint       // from User-Agent header
    ) {}

    record RefreshTokenCommand(
        String rawRefreshToken, // the token sent by client
        String ipAddress,       // for audit logging
        String deviceHint       // from User-Agent header
    ) {}

    record LogoutCommand(
        UUID userId,            // from JWT
        String rawRefreshToken  // token to revoke
    ) {}

    record LogoutAllCommand(
        UUID userId             // revoke all tokens for this user
    ) {}

    // ── Response objects ───────────────────────────────────────────────────

    record TokenPairDto(
        String accessToken,     // JWT — store in memory on client
        String refreshToken,    // opaque — store in HttpOnly cookie
        UUID userId,
        String email,
        String onboardingStep,
        long expiresIn          // access token expiry in seconds
    ) {}

    record AuthUserDto(
        UUID userId,
        String email,
        boolean emailVerified,
        String provider,
        String onboardingStep,
        String displayName,
        String avatarUrl
    ) {}

    record SessionDto(
        UUID sessionId,
        String deviceHint,
        String ipAddress,
        String createdAt,
        String expiresAt,
        boolean isCurrent
    ) {}
}
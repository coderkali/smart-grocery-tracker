package com.smartgrocery.modules.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

// Maps to refresh_token table
// Stores hashed refresh tokens — never the raw token value
@Table("refresh_token")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    private UUID id;

    // Foreign key to user_account.id
    @Column("user_id")
    private UUID userId;

    // SHA-256 hash of the actual token
    // Raw token is sent to client once and never stored
    // On refresh request — we hash the incoming token and look this up
    @Column("token_hash")
    private String tokenHash;

    // Groups tokens belonging to the same session
    // If a revoked token is used — we revoke ALL tokens in this family
    // This detects token theft — attacker uses stolen token,
    // we see reuse, we kill the entire session family
    @Column("family")
    private UUID family;

    // Browser/device hint from User-Agent header
    // Shown in "active sessions" list so user knows which device
    @Column("device_hint")
    private String deviceHint;

    // IP address when token was created
    @Column("ip_address")
    private String ipAddress;

    // When this token expires — 7 days from creation
    @Column("expires_at")
    private Instant expiresAt;

    // Set when token is revoked — null means token is still valid
    @Column("revoked_at")
    private Instant revokedAt;

    // Why was it revoked — LOGOUT, REUSE_DETECTED, LOGOUT_ALL, EXPIRED
    @Column("revoke_reason")
    private String revokeReason;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    // ── Business logic ─────────────────────────────────────────────────────

    // Token is valid only if not revoked AND not expired
    public boolean isValid() {
        return this.revokedAt == null
            && this.expiresAt.isAfter(Instant.now());
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public boolean isExpired() {
        return this.expiresAt.isBefore(Instant.now());
    }

    // Revoke this token with a reason
    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revokeReason = reason;
    }
}
package com.smartgrocery.modules.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

// Maps to user_identity table
// One UserAccount can have multiple UserIdentity rows
// Example: same user logged in with Google AND GitHub
@Table("user_identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentity {

    @Id
    private UUID id;

    // Foreign key to user_account.id
    // R2DBC does NOT do joins automatically like JPA
    // We store the UUID and load UserAccount separately when needed
    @Column("user_id")
    private UUID userId;

    // OAuth2 provider name — "google" or "github"
    @Column("provider")
    private String provider;

    // The unique ID from the provider (e.g. Google's sub claim)
    // This never changes even if user changes their email
    @Column("provider_id")
    private String providerId;

    // Email from the provider — may differ from user_account.email
    @Column("provider_email")
    private String providerEmail;

    // Display name from the provider
    @Column("provider_name")
    private String providerName;

    @Column("avatar_url")
    private String avatarUrl;

    // True if this is the provider the user first signed up with
    @Column("is_primary")
    private Boolean isPrimary;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;

    // ── Business logic ─────────────────────────────────────────────────────

    public boolean isGoogle() {
        return "google".equalsIgnoreCase(this.provider);
    }

    public boolean isGithub() {
        return "github".equalsIgnoreCase(this.provider);
    }
}
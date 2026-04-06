package com.smartfinvo.modules.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// Full user profile returned by GET /api/v1/users/me
// Only exposes fields that are safe to return to the authenticated user
// Sensitive fields (failedLoginAttempts, deletedAt) are intentionally excluded
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User profile returned by the API")
public class UserResponse {

    // Primary key — UUID assigned by PostgreSQL on account creation
    @Schema(description = "Unique user ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "User's email address", example = "user@example.com")
    private String email;

    // Whether the email was verified by the OAuth2 provider (always true for Google OAuth2)
    @JsonProperty("email_verified")
    @Schema(description = "Whether the email address was verified", example = "true")
    private Boolean emailVerified;

    // Full display name — sourced from OAuth2 provider on first login
    @JsonProperty("display_name")
    @Schema(description = "Display name shown in the UI", example = "Jane Doe")
    private String displayName;

    @JsonProperty("first_name")
    @Schema(description = "First name", example = "Jane")
    private String firstName;

    @JsonProperty("last_name")
    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    // URL to the user's profile picture — served by the OAuth2 provider (e.g., Google CDN)
    @JsonProperty("avatar_url")
    @Schema(description = "URL to the user's profile picture", example = "https://lh3.googleusercontent.com/...")
    private String avatarUrl;

    // ACTIVE | SUSPENDED | LOCKED | DELETED
    @Schema(description = "Account status", example = "ACTIVE")
    private String status;

    // Tracks where the user is in the onboarding flow:
    // ACCOUNT_CREATED → CARD_CONNECTED → CATEGORIES_SET → BUDGET_SET → COMPLETED
    @JsonProperty("onboarding_step")
    @Schema(description = "Current onboarding step", example = "COMPLETED")
    private String onboardingStep;

    // Optimistic locking version — exposed so clients can detect concurrent edits
    @Schema(description = "Optimistic locking version", example = "1")
    private Integer version;

    @JsonProperty("created_at")
    @Schema(description = "Account creation timestamp (ISO-8601 UTC)", example = "2024-01-15T10:30:00Z")
    private Instant createdAt;

    @JsonProperty("updated_at")
    @Schema(description = "Last update timestamp (ISO-8601 UTC)", example = "2024-06-20T14:22:00Z")
    private Instant updatedAt;
}

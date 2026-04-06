package com.smartfinvo.modules.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request body for PATCH /api/v1/users/me
// All fields are optional — only provided (non-null) fields are applied
// Follows the same partial-update pattern as UpdateExpenseRequest
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for updating your user profile. All fields are optional — only provided fields are changed.")
public class UpdateUserProfileRequest {

    // Display name shown in the UI — e.g. "Jane Doe" or a custom nickname
    @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
    @JsonProperty("display_name")
    @Schema(description = "Display name shown in the UI (2–100 chars)", example = "Jane Doe")
    private String displayName;

    @Size(max = 100, message = "First name must be 100 characters or fewer")
    @JsonProperty("first_name")
    @Schema(description = "First name (max 100 chars)", example = "Jane")
    private String firstName;

    @Size(max = 100, message = "Last name must be 100 characters or fewer")
    @JsonProperty("last_name")
    @Schema(description = "Last name (max 100 chars)", example = "Doe")
    private String lastName;

    // URL to the user's profile picture — caller is responsible for uploading to storage first
    @Size(max = 2048, message = "Avatar URL must be 2048 characters or fewer")
    @JsonProperty("avatar_url")
    @Schema(description = "URL to the user's profile picture (max 2048 chars)", example = "https://storage.example.com/avatars/jane.jpg")
    private String avatarUrl;
}

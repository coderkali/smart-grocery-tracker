package com.smartfinvo.modules.user.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request body for DELETE /api/v1/users/me
// The caller must explicitly set confirmDeletion = true.
// @AssertTrue ensures Spring validation rejects false or null — the client
// cannot accidentally delete their account by sending an empty body.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body to confirm permanent account deletion.")
public class DeleteAccountRequest {

    // Must be explicitly true — Spring @Valid will reject false or null
    @AssertTrue(message = "confirmDeletion must be true to delete your account")
    @Schema(
            description = "Must be set to true to confirm deletion. Sending false or omitting this field will return 400.",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean confirmDeletion;

    // Optional — stored in the revoke_reason on refresh tokens for audit purposes
    @Schema(description = "Optional reason for leaving (stored for audit)", example = "No longer needed")
    private String reason;
}

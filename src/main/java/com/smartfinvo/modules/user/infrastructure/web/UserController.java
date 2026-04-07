package com.smartfinvo.modules.user.infrastructure.web;

import com.smartfinvo.modules.user.application.UserService;
import com.smartfinvo.modules.user.infrastructure.web.dto.UpdateUserProfileRequest;
import com.smartfinvo.modules.user.infrastructure.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.smartfinvo.modules.user.infrastructure.web.dto.DeleteAccountRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

// All user profile REST endpoints live here
// Controller is thin — no business logic
// Just: extract userId from request attribute → call UserService → shape response
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile management — view and update your account.")
public class UserController {

    private final UserService userService;

    // ── GET /api/v1/users/me ──────────────────────────────────────────────
    // Returns the full profile of the currently authenticated user
    // userId is injected by JwtAuthenticationFilter from the validated JWT —
    // the client never needs to pass their own ID
    @Operation(
            summary = "Get current user profile",
            description = """
                    Returns the full profile of the authenticated user.
                    The user ID is extracted from the JWT — no path parameter needed.
                    """,
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User profile returned",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid JWT — re-authenticate via /auth/refresh"),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found — account may have been deleted")
    })
    @GetMapping("/me")
    public Mono<ResponseEntity<UserResponse>> getCurrentUser(
            // userId is set in the request attributes by JwtAuthenticationFilter
            // @Parameter(hidden = true) hides this from the Swagger UI — it is not a request param
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        return userService
                .getCurrentUser(userId)
                // UserService returns the DTO directly — wrap it in 200 OK
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    // USER_NOT_FOUND covers both missing rows and soft-deleted accounts
                    if ("USER_NOT_FOUND".equals(error.getMessage())) {
                        log.warn("GET /users/me — user not found userId={}", userId);
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    // Unexpected errors — log with full detail, return generic 500
                    log.error("GET /users/me — unexpected error userId={} error={}", userId, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ── PATCH /api/v1/users/me ────────────────────────────────────────────
    // Partially updates the authenticated user's profile
    // Only fields present in the request body are changed — omitted fields stay as-is
    @Operation(
            summary = "Update current user profile",
            description = """
                    Partially updates the authenticated user's profile.
                    Only the fields you include in the request body are changed.
                    Omitted fields are left unchanged.
                    """,
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile updated — full updated profile returned",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed — check field constraints in the request body"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid JWT"),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found — account may have been deleted"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Optimistic locking conflict — another request modified this profile concurrently, retry")
    })
    @PatchMapping("/me")
    public Mono<ResponseEntity<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateUserProfileRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        return userService
                .updateProfile(userId, request)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    if ("USER_NOT_FOUND".equals(error.getMessage())) {
                        log.warn("PATCH /users/me — user not found userId={}", userId);
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    // OptimisticLockingFailureException — version conflict, client should retry
                    if (error instanceof org.springframework.dao.OptimisticLockingFailureException) {
                        log.warn("PATCH /users/me — version conflict userId={}", userId);
                        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
                    }
                    log.error("PATCH /users/me — unexpected error userId={} error={}", userId, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ── GET /api/v1/users/{userId} ────────────────────────────────────────
    // Admin-only: look up any user's profile by their UUID
    // The requesting user must have isAdmin = true on their UserAccount row —
    // checked in UserService before the target user is loaded
    @Operation(
            summary = "Get user by ID (admin only)",
            description = """
                    Fetches the full profile of any user by their UUID.
                    Requires the authenticated user to have admin privileges (`is_admin = true`).
                    Returns 403 Forbidden for non-admin callers even if the target user exists.
                    """,
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User profile returned",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid JWT"),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller does not have admin privileges"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Target user not found or has been deleted")
    })
    @GetMapping("/{userId}")
    public Mono<ResponseEntity<UserResponse>> getUserById(
            @Parameter(description = "UUID of the user to look up", required = true)
            @PathVariable UUID userId,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID requestingUserId) {

        return userService
                .getUserById(requestingUserId, userId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    if ("FORBIDDEN".equals(error.getMessage())) {
                        log.warn("GET /users/{} — forbidden requestingUserId={}", userId, requestingUserId);
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }
                    if ("USER_NOT_FOUND".equals(error.getMessage())) {
                        log.warn("GET /users/{} — not found requestingUserId={}", userId, requestingUserId);
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    log.error("GET /users/{} — unexpected error requestingUserId={} error={}",
                            userId, requestingUserId, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ── DELETE /api/v1/users/me ───────────────────────────────────────────
    // Permanently (soft) deletes the authenticated user's account.
    // Caller must send { "confirmDeletion": true } — Spring @Valid rejects anything else.
    // On success: 204 No Content. Client must discard their JWT immediately.
    @Operation(
            summary = "Delete current user account",
            description = """
                    Soft-deletes the authenticated user's account and revokes all active sessions.
                    The request body must contain `confirmDeletion: true` — this prevents accidental deletion.
                    Returns 204 No Content on success. The JWT remains technically valid until it expires,
                    so the client must discard it immediately after this call.
                    """,
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Account deleted — all sessions revoked"),
            @ApiResponse(
                    responseCode = "400",
                    description = "confirmDeletion was false or missing"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid JWT"),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found — already deleted")
    })
    @DeleteMapping("/me")
    public Mono<ResponseEntity<Void>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        Mono<ResponseEntity<Void>> noContent = Mono.just(ResponseEntity.noContent().build());
        return userService
                .deleteAccount(userId, request)
                .then(noContent)
                .onErrorResume(error -> {
                    if ("USER_NOT_FOUND".equals(error.getMessage())) {
                        log.warn("DELETE /users/me — user not found userId={}", userId);
                        ResponseEntity<Void> r = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                        return Mono.just(r);
                    }
                    log.error("DELETE /users/me — unexpected error userId={} error={}", userId, error.getMessage());
                    ResponseEntity<Void> r = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    return Mono.just(r);
                });
    }
}

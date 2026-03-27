package com.smartfinvo.modules.auth.infrastructure.web;

import com.smartfinvo.modules.auth.api.AuthModulePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

// All auth REST endpoints live here
// Controller is thin — no business logic
// Just: extract input → call AuthService → shape response
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Session management — token rotation, logout, user profile, and active sessions. Login itself is handled by OAuth2 redirect: `GET /oauth2/authorization/google`.")
public class AuthController {

  private final AuthModulePort authService;

  // ── POST /api/v1/auth/refresh ─────────────────────────────────────────
  // Client calls this when their access token expires (401)
  // Refresh token comes automatically from the HttpOnly cookie
  // Returns new access token in body + new refresh token in cookie
  @Operation(
      summary = "Rotate tokens",
      description = """
          Exchange a valid refresh token for a new access + refresh token pair.
          The refresh token is read automatically from the `refresh_token` HttpOnly cookie — no request body needed.
          A new refresh token is written back to the cookie on success.
          """)
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Tokens rotated successfully",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(value = """
                  {
                    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                    "email": "user@example.com",
                    "onboardingStep": "COMPLETED",
                    "expiresIn": 900
                  }"""))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid refresh token",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(value = "{\"error\": \"No refresh token\"}")))
  })
  @PostMapping("/refresh")
  public Mono<ResponseEntity<Map<String, Object>>> refresh(ServerWebExchange exchange) {

    // Read refresh token from HttpOnly cookie
    // Client cannot access this cookie via JavaScript — browser sends it automatically
    HttpCookie cookie = exchange.getRequest().getCookies().getFirst("refresh_token");

    if (cookie == null) {
      return Mono.just(
          ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No refresh token")));
    }

    String ipAddress = getClientIp(exchange.getRequest());
    String deviceHint = getDeviceHint(exchange.getRequest());

    AuthModulePort.RefreshTokenCommand command =
        new AuthModulePort.RefreshTokenCommand(cookie.getValue(), ipAddress, deviceHint);

    return authService
        .refreshTokens(command)
        .flatMap(
            tokens -> {
              // Set new refresh token cookie
              setRefreshCookie(exchange.getResponse(), tokens.refreshToken());

              return Mono.just(
                  ResponseEntity.ok(
                      Map.<String, Object>of(
                          "accessToken", tokens.accessToken(),
                          "userId", tokens.userId().toString(),
                          "email", tokens.email(),
                          "onboardingStep", tokens.onboardingStep(),
                          "expiresIn", tokens.expiresIn())));
            })
        .onErrorResume(
            error -> {
              log.warn("Token refresh failed — {}", error.getMessage());
              return Mono.just(
                  ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                      .body(Map.<String, Object>of("error", error.getMessage())));
            });
  }

  // ── POST /api/v1/auth/logout ──────────────────────────────────────────
  // Revokes current device session only
  // userId extracted from JWT by JwtAuthenticationFilter (Step 11)
  @Operation(
      summary = "Logout current device",
      description = "Revokes the refresh token for the current device session only. Clears the `refresh_token` cookie. Idempotent — returns 200 even if already logged out.",
      security = @SecurityRequirement(name = "BearerAuth"))
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Logged out",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(value = "{\"message\": \"Logged out successfully\"}"))),
      @ApiResponse(responseCode = "500", description = "Internal error during logout")
  })
  @PostMapping("/logout")
  public Mono<ResponseEntity<Map<String, String>>> logout(
      ServerWebExchange exchange,
      @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

    HttpCookie cookie = exchange.getRequest().getCookies().getFirst("refresh_token");

    if (cookie == null) {
      // No cookie — already logged out, return success anyway (idempotent)
      clearRefreshCookie(exchange.getResponse());
      return Mono.just(ResponseEntity.ok(Map.of("message", "Logged out")));
    }

    AuthModulePort.LogoutCommand command =
        new AuthModulePort.LogoutCommand(userId, cookie.getValue());

    return authService
        .logout(command)
        .then(
            Mono.fromCallable(
                () -> {
                  clearRefreshCookie(exchange.getResponse());
                  return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
                }))
        .onErrorResume(
            error -> {
              log.error("Logout failed userId={} error={}", userId, error.getMessage());
              return Mono.just(
                  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                      .body(Map.of("error", "Logout failed")));
            });
  }

  // ── POST /api/v1/auth/logout/all ─────────────────────────────────────
  // Revokes ALL sessions for this user across all devices
  @Operation(
      summary = "Logout all devices",
      description = "Revokes every active refresh token for this user across all devices and browsers. Use this after a suspected account compromise.",
      security = @SecurityRequirement(name = "BearerAuth"))
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "All sessions revoked",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(value = """
                  {"message": "All sessions revoked", "sessionsRevoked": 3}"""))),
      @ApiResponse(responseCode = "500", description = "Internal error")
  })
  @PostMapping("/logout/all")
  public Mono<ResponseEntity<Map<String, Object>>> logoutAll(
      ServerWebExchange exchange,
      @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

    AuthModulePort.LogoutAllCommand command = new AuthModulePort.LogoutAllCommand(userId);

    return authService
        .logoutAll(command)
        .map(
            count -> {
              clearRefreshCookie(exchange.getResponse());
              return ResponseEntity.ok(
                  Map.<String, Object>of(
                      "message", "All sessions revoked", "sessionsRevoked", count));
            })
        .onErrorResume(
            error -> {
              log.error("LogoutAll failed userId={} error={}", userId, error.getMessage());
              return Mono.just(
                  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                      .body(Map.of("error", "Logout all failed")));
            });
  }

  // ── GET /api/v1/auth/me ───────────────────────────────────────────────
  // Returns current user profile
  // userId injected by JwtAuthenticationFilter from the JWT
  @Operation(
      summary = "Get current user profile",
      description = "Returns the authenticated user's profile extracted from the JWT.",
      security = @SecurityRequirement(name = "BearerAuth"))
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "User profile",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = AuthModulePort.AuthUserDto.class))),
      @ApiResponse(responseCode = "404", description = "User not found")
  })
  @GetMapping("/me")
  public Mono<ResponseEntity<AuthModulePort.AuthUserDto>> me(
      @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

    return authService
        .getCurrentUser(userId)
        .map(ResponseEntity::ok)
        .onErrorResume(
            error -> {
              log.warn("Get user failed userId={} error={}", userId, error.getMessage());
              return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
            });
  }

  // ── GET /api/v1/auth/sessions ─────────────────────────────────────────
  // Returns all active sessions for the current user
  @Operation(
      summary = "List active sessions",
      description = "Returns all active login sessions for the current user across all devices.",
      security = @SecurityRequirement(name = "BearerAuth"))
  @ApiResponse(responseCode = "200", description = "List of active sessions",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = AuthModulePort.SessionDto.class)))
  @GetMapping("/sessions")
  public Flux<AuthModulePort.SessionDto> sessions(
      @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
    return authService.getActiveSessions(userId);
  }

  // ── DELETE /api/v1/auth/sessions/{sessionId} ──────────────────────────
  // Revokes a specific session by ID
  // User can only revoke their own sessions (AuthService validates this)
  @Operation(
      summary = "Revoke a specific session",
      description = "Revokes one session by its ID. Users can only revoke their own sessions.",
      security = @SecurityRequirement(name = "BearerAuth"))
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Session revoked",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(value = "{\"message\": \"Session revoked\"}"))),
      @ApiResponse(responseCode = "403", description = "Session belongs to another user"),
      @ApiResponse(responseCode = "404", description = "Session not found")
  })
  @DeleteMapping("/sessions/{sessionId}")
  public Mono<ResponseEntity<Map<String, String>>> revokeSession(
      @Parameter(description = "UUID of the session to revoke", required = true)
      @PathVariable UUID sessionId,
      @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

    return authService
        .revokeSession(userId, sessionId)
        .then(Mono.just(ResponseEntity.ok(Map.of("message", "Session revoked"))))
        .onErrorResume(
            error -> {
              log.warn(
                  "Revoke session failed userId={} sessionId={} error={}",
                  userId,
                  sessionId,
                  error.getMessage());

              HttpStatus status =
                  error.getMessage().equals("UNAUTHORIZED")
                      ? HttpStatus.FORBIDDEN
                      : HttpStatus.NOT_FOUND;

              return Mono.just(
                  ResponseEntity.status(status).body(Map.of("error", error.getMessage())));
            });
  }

  // ── Cookie helpers ────────────────────────────────────────────────────

  // Set refresh token as HttpOnly cookie
  private void setRefreshCookie(ServerHttpResponse response, String rawToken) {
    ResponseCookie cookie =
        ResponseCookie.from("refresh_token", rawToken)
            .httpOnly(true) // JavaScript cannot read this
            .secure(true) // HTTPS only
            .path("/api/v1/auth") // Only sent to auth endpoints
            .maxAge(Duration.ofDays(7))
            .sameSite("Strict") // CSRF protection
            .build();
    response.addCookie(cookie);
  }

  // Clear cookie on logout — set maxAge to 0
  private void clearRefreshCookie(ServerHttpResponse response) {
    ResponseCookie cookie =
        ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(true)
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO) // Tells browser to delete the cookie
            .sameSite("Strict")
            .build();
    response.addCookie(cookie);
  }

  // Get real client IP — checks X-Forwarded-For for load balancers
  private String getClientIp(ServerHttpRequest request) {
    String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
    if (forwarded != null && !forwarded.isEmpty()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddress() != null
        ? request.getRemoteAddress().getAddress().getHostAddress()
        : "unknown";
  }

  // Short device hint from User-Agent
  private String getDeviceHint(ServerHttpRequest request) {
    String ua = request.getHeaders().getFirst("User-Agent");
    if (ua == null) return "Unknown device";
    return ua.length() > 100 ? ua.substring(0, 100) : ua;
  }
}

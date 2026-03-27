package com.smartgrocery.modules.auth.infrastructure.web;

import com.smartgrocery.modules.auth.api.AuthModulePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
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
public class AuthController {

  private final AuthModulePort authService;

  // ── POST /api/v1/auth/refresh ─────────────────────────────────────────
  // Client calls this when their access token expires (401)
  // Refresh token comes automatically from the HttpOnly cookie
  // Returns new access token in body + new refresh token in cookie
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
  @PostMapping("/logout")
  public Mono<ResponseEntity<Map<String, String>>> logout(
      ServerWebExchange exchange, @RequestAttribute("userId") UUID userId) {

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
  @PostMapping("/logout/all")
  public Mono<ResponseEntity<Map<String, Object>>> logoutAll(
      ServerWebExchange exchange, @RequestAttribute("userId") UUID userId) {

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
  @GetMapping("/me")
  public Mono<ResponseEntity<AuthModulePort.AuthUserDto>> me(
      @RequestAttribute("userId") UUID userId) {

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
  @GetMapping("/sessions")
  public Flux<AuthModulePort.SessionDto> sessions(@RequestAttribute("userId") UUID userId) {
    return authService.getActiveSessions(userId);
  }

  // ── DELETE /api/v1/auth/sessions/{sessionId} ──────────────────────────
  // Revokes a specific session by ID
  // User can only revoke their own sessions (AuthService validates this)
  @DeleteMapping("/sessions/{sessionId}")
  public Mono<ResponseEntity<Map<String, String>>> revokeSession(
      @PathVariable UUID sessionId, @RequestAttribute("userId") UUID userId) {

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

package com.smartfinvo.modules.auth.infrastructure.web;

import com.smartfinvo.modules.auth.api.AuthModulePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.util.Map;

// Spring Security calls this automatically after it has:
//   1. Received the ?code= from Google
//   2. Exchanged it with Google's token endpoint (server to server)
//   3. Got back the user's email, name, googleId
//
// By the time we get here — OAuth2User already has all the info.
// We just pull it out and hand it to AuthService.
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final AuthModulePort authService;

    // Redirect URL after successful login
    // In production this would be your frontend URL
    private static final String FRONTEND_URL = "/dashboard";

    @Override
    public Mono<Void> onAuthenticationSuccess(
            WebFilterExchange exchange, Authentication authentication) {

        // Spring has already verified the user with Google by this point
        // OAuth2User contains everything Google returned
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // Pull user info from the OAuth2User object
        // "sub"     — Google's unique stable ID for this user (never changes)
        // "email"   — user's Google email
        // "name"    — display name
        // "picture" — avatar URL
        String providerId = oAuth2User.getAttribute("sub");
        String email      = oAuth2User.getAttribute("email");
        String name       = oAuth2User.getAttribute("name");
        String avatar     = oAuth2User.getAttribute("picture");

        // Determine which provider (google / github)
        // We check the registration ID Spring stored in the auth object
        String provider = extractProvider(authentication);

        // Get request metadata for session tracking
        ServerWebExchange webExchange = exchange.getExchange();
        String ipAddress  = getClientIp(webExchange);
        String deviceHint = getDeviceHint(webExchange);

        log.info("OAuth2 success handler — provider={} email={}",
                provider, maskEmail(email));

        // Build the command and call AuthService
        AuthModulePort.OAuth2LoginCommand command = new AuthModulePort.OAuth2LoginCommand(
                provider,
                providerId,
                email,
                name,
                avatar,
                ipAddress,
                deviceHint
        );

        return authService.processOAuth2Login(command)
                .flatMap(tokens -> {
                    // Set refresh token as HttpOnly cookie
                    // HttpOnly = JavaScript cannot read this cookie
                    // Secure   = only sent over HTTPS
                    // SameSite = Strict prevents CSRF attacks
                    ResponseCookie refreshCookie = ResponseCookie
                            .from("refresh_token", tokens.refreshToken())
                            .httpOnly(true)
                            .secure(true)
                            .path("/api/v1/auth")
                            .maxAge(Duration.ofDays(7))
                            .sameSite("Strict")
                            .build();

                    webExchange.getResponse().addCookie(refreshCookie);

                    // Return JSON response with access token
                    // User can copy the accessToken and use it in Postman
                    webExchange.getResponse().setStatusCode(HttpStatus.OK);
                    webExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

                    Map<String, Object> body = Map.of(
                            "accessToken", tokens.accessToken(),
                            "userId", tokens.userId().toString(),
                            "email", tokens.email(),
                            "onboardingStep", tokens.onboardingStep(),
                            "expiresIn", tokens.expiresIn()
                    );

                    try {
                        byte[] bytes = new ObjectMapper().writeValueAsBytes(body);
                        DataBuffer buffer = webExchange.getResponse().bufferFactory().wrap(bytes);
                        return webExchange.getResponse().writeWith(Mono.just(buffer));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .doOnError(error ->
                    log.error("OAuth2 login failed provider={} error={}",
                            provider, error.getMessage()));
    }

    // Extract provider name from the authentication object
    // Spring stores it as the registration ID from application.yml
    private String extractProvider(Authentication authentication) {
        if (authentication.getPrincipal() instanceof
                org.springframework.security.oauth2.core.user.DefaultOAuth2User) {
            // Pull from the OAuth2AuthenticationToken
            if (authentication instanceof
                    org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken token) {
                return token.getAuthorizedClientRegistrationId(); // "google" or "github"
            }
        }
        return "google"; // safe default
    }

    // Get real client IP — checks X-Forwarded-For first (for load balancers)
    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    // Extract device hint from User-Agent header
    // Shown in "active sessions" list so user knows which device
    private String getDeviceHint(ServerWebExchange exchange) {
        String userAgent = exchange.getRequest()
                .getHeaders()
                .getFirst("User-Agent");
        if (userAgent == null) return "Unknown device";
        // Keep it short — just first 100 chars
        return userAgent.length() > 100
                ? userAgent.substring(0, 100)
                : userAgent;
    }

    // Never log full email — GDPR compliance
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        return parts[0].charAt(0) + "***@" + parts[1];
    }
}
package com.smartfinvo.modules.auth.infrastructure.web;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// Runs before every request
// Job: read JWT → validate → put userId in request attributes
// Controllers then access userId via @RequestAttribute("userId")
//
// If JWT is missing or invalid on a protected route → 401
// If route is public (login, refresh) → skip validation
@Slf4j
@Component
public class JwtAuthenticationFilter implements WebFilter {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // These paths do NOT require a valid JWT
    // Everything else does
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/refresh",
            "/login",
            "/oauth2",
            "/actuator/health",
            "/api/v1/ai/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs",
            "/webjars/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip JWT check for public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Read Authorization header
        // Expected format: "Bearer eyJhbGc..."
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No Bearer token on protected path={}", path);
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7); // strip "Bearer "

        try {
            // Parse and validate the JWT
            // This checks: signature, expiry, format
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(
                            jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Extract userId from the "sub" claim
            String userId = claims.getSubject();

            // Put userId into request attributes
            // Controllers read it with @RequestAttribute("userId")
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(headers ->
                            headers.add("X-User-Id", userId)))
                    .build();

            // Also store as attribute for @RequestAttribute
            mutated.getAttributes().put("userId", UUID.fromString(userId));

            // Set Spring Security authentication context
            // Without this, Spring Security's .anyExchange().authenticated() check fails
            // and redirects to OAuth2 login page even with a valid JWT
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

            log.debug("JWT valid userId={} path={}", userId, path);

            return chain.filter(mutated)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));

        } catch (ExpiredJwtException e) {
            // Token is valid but expired — client should call /refresh
            log.debug("JWT expired path={}", path);
            return unauthorized(exchange, "Token expired");

        } catch (Exception e) {
            // Token is malformed, wrong signature, or tampered with
            log.warn("JWT invalid path={} error={}", path, e.getMessage());
            return unauthorized(exchange, "Invalid token");
        }
    }

    // Check if this path is in the public list
    // Uses startsWith so /oauth2/callback also matches /oauth2
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(publicPath -> {
            if (publicPath.endsWith("/**")) {
                // Strip /** and check if path starts with the prefix
                String prefix = publicPath.substring(0, publicPath.length() - 3);
                return path.startsWith(prefix);
            }
            return path.startsWith(publicPath);
        });
    }

    // Write 401 response and stop the filter chain
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders()
                .add("Content-Type", "application/json");

        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        org.springframework.core.io.buffer.DataBuffer buffer =
                exchange.getResponse().bufferFactory().wrap(body);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
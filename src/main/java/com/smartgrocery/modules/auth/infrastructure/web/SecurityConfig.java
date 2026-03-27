package com.smartgrocery.modules.auth.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler successHandler;
    private final JwtAuthenticationFilter  jwtFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http

            // ── Disable sessions ───────────────────────────────────────────
            // We are stateless — JWT handles identity, not server sessions
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

            // ── Disable CSRF ───────────────────────────────────────────────
            // Safe because:
            // 1. Refresh token cookie is SameSite=Strict
            // 2. Access token is in Authorization header (not a cookie)
            // 3. We are a pure API — no browser form submissions
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            // ── Route rules ────────────────────────────────────────────────
            .authorizeExchange(exchanges -> exchanges

                // Public — no JWT needed
                .pathMatchers(
                    "/login/**",
                    "/oauth2/**",
                    "/api/v1/auth/refresh",
                    "/actuator/health"
                ).permitAll()

                // Everything else requires a valid JWT
                .anyExchange().authenticated()
            )

            // ── OAuth2 login ───────────────────────────────────────────────
            // Handles the Google redirect flow automatically
            // On success → calls our OAuth2LoginSuccessHandler
            .oauth2Login(oauth2 -> oauth2
                .authenticationSuccessHandler(successHandler)
            )

            // ── JWT filter ─────────────────────────────────────────────────
            // Runs before Spring's auth checks on every request
            // Reads Bearer token → validates → puts userId in request
            .addFilterBefore(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)

            .build();
    }
}
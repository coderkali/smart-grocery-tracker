package com.smartfinvo.modules.auth.application;

import com.smartfinvo.modules.auth.api.AuthModulePort;
import com.smartfinvo.modules.auth.infrastructure.cache.RefreshTokenCacheRepository;
import com.smartfinvo.modules.auth.infrastructure.persistence.RefreshTokenRepository;
import com.smartfinvo.modules.auth.infrastructure.persistence.UserAccountRepository;
import com.smartfinvo.modules.auth.infrastructure.persistence.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

// @SpringBootTest — loads the full Spring context
// No mocks — real PostgreSQL, real Redis, real AuthService
// Testcontainers spins up fresh containers for each test run
@SpringBootTest
@Testcontainers
@org.junit.jupiter.api.Disabled("Disabled — Docker socket incompatibility on Mac with Testcontainers. Re-enable in CI/CD pipeline.")
class AuthServiceIntegrationTest {

    // ── Testcontainers — spins up real PostgreSQL ─────────────────────────
    // static = one container shared across all tests in this class
    // Faster than starting a new container per test
    static {
        // Fix for Docker Desktop on Mac — non-standard socket path
        System.setProperty("DOCKER_HOST",
                "unix:///Users/kaliprasad/.docker/run/docker.sock");
        System.setProperty("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE",
                "/Users/kaliprasad/.docker/run/docker.sock");
    }


    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("smartgrocery_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    // ── Testcontainers — spins up real Redis ──────────────────────────────
    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // ── Wire Testcontainer ports into Spring config ───────────────────────
    // Spring reads these BEFORE creating beans
    // So repositories connect to test containers, not your local Docker
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://"
            + postgres.getHost() + ":"
            + postgres.getMappedPort(5432)
            + "/smartgrocery_test");
        registry.add("spring.r2dbc.username", () -> "test_user");
        registry.add("spring.r2dbc.password", () -> "test_pass");

        // Flyway needs JDBC url (not R2DBC) for migrations
        registry.add("spring.flyway.url",      postgres::getJdbcUrl);
        registry.add("spring.flyway.user",     () -> "test_user");
        registry.add("spring.flyway.password", () -> "test_pass");

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () ->
            redis.getMappedPort(6379).toString());
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired private AuthModulePort           authService;
    @Autowired private UserAccountRepository    userAccountRepository;
    @Autowired private UserIdentityRepository   userIdentityRepository;
    @Autowired private RefreshTokenRepository   refreshTokenRepository;
    @Autowired private RefreshTokenCacheRepository refreshTokenCacheRepository;

    // ── Shared test command ───────────────────────────────────────────────
    private final AuthModulePort.OAuth2LoginCommand GOOGLE_LOGIN =
        new AuthModulePort.OAuth2LoginCommand(
            "google", "google-sub-123", "john@gmail.com",
            "John Doe", "https://avatar.url",
            "127.0.0.1", "Chrome/Mac"
        );

    @BeforeEach
    void cleanDatabase() {
        // Wipe all tables before each test — clean slate every time
        refreshTokenRepository.deleteAll()
            .then(userIdentityRepository.deleteAll())
            .then(userAccountRepository.deleteAll())
            .block();
    }

    // ════════════════════════════════════════════════════════════════════
    // Full login flow
    // ════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Full login flow")
    class FullLoginFlow {

        @Test
        @DisplayName("New user login — creates account, identity, tokens in DB and Redis")
        void newUserLogin_createsEverything() {
            StepVerifier.create(authService.processOAuth2Login(GOOGLE_LOGIN))
                    .assertNext(tokens -> {
                        assertThat(tokens.accessToken()).isNotBlank();
                        assertThat(tokens.refreshToken()).isNotBlank();
                        assertThat(tokens.email()).isEqualTo("john@gmail.com");
                        assertThat(tokens.onboardingStep()).isEqualTo("ACCOUNT_CREATED");
                    })
                    .verifyComplete();

            // Verify account was actually saved in PostgreSQL
            StepVerifier.create(userAccountRepository.findByEmail("john@gmail.com"))
                    .assertNext(user -> {
                        assertThat(user.getEmail()).isEqualTo("john@gmail.com");
                        assertThat(user.getStatus()).isEqualTo("ACTIVE");
                    })
                    .verifyComplete();

            // Verify identity was saved
            StepVerifier.create(
                    userIdentityRepository.findByProviderAndProviderId("google", "google-sub-123"))
                    .assertNext(identity -> {
                        assertThat(identity.getProvider()).isEqualTo("google");
                        assertThat(identity.getIsPrimary()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Same user logs in twice — no duplicate account created")
        void sameUserLoginTwice_noDuplicate() {
            // Login twice with same Google account
            authService.processOAuth2Login(GOOGLE_LOGIN).block();
            authService.processOAuth2Login(GOOGLE_LOGIN).block();

            // Should still only be one user account
            StepVerifier.create(
                    userAccountRepository.findByEmail("john@gmail.com").flux().count())
                    .assertNext(count -> assertThat(count).isEqualTo(1))
                    .verifyComplete();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Token refresh flow
    // ════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Token refresh flow")
    class TokenRefreshFlow {

        @Test
        @DisplayName("Valid refresh token — issues new token pair, old token revoked")
        void validRefreshToken_issuesNewPair() {
            // Login first to get tokens
            AuthModulePort.TokenPairDto firstLogin =
                authService.processOAuth2Login(GOOGLE_LOGIN).block();

            assertThat(firstLogin).isNotNull();
            String oldRefreshToken = firstLogin.refreshToken();

            // Now refresh
            AuthModulePort.RefreshTokenCommand refreshCmd =
                new AuthModulePort.RefreshTokenCommand(
                    oldRefreshToken, "127.0.0.1", "Chrome/Mac");

            StepVerifier.create(authService.refreshTokens(refreshCmd))
                    .assertNext(newTokens -> {
                        assertThat(newTokens.accessToken()).isNotBlank();
                        assertThat(newTokens.refreshToken()).isNotBlank();
                        // New refresh token must be different from old one
                        assertThat(newTokens.refreshToken())
                                .isNotEqualTo(oldRefreshToken);
                    })
                    .verifyComplete();

            // Old refresh token must now be revoked in DB
            StepVerifier.create(refreshTokenRepository.findAll()
                    .filter(t -> t.getRevokeReason() != null
                              && t.getRevokeReason().equals("ROTATED")))
                    .assertNext(revokedToken -> {
                        assertThat(revokedToken.getRevokedAt()).isNotNull();
                        assertThat(revokedToken.getRevokeReason()).isEqualTo("ROTATED");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Reuse detection — using old rotated token kills entire family")
        void tokenReuse_killsEntireFamily() {
            // Login
            AuthModulePort.TokenPairDto firstLogin =
                authService.processOAuth2Login(GOOGLE_LOGIN).block();
            String stolenToken = firstLogin.refreshToken();

            // Legitimate refresh — rotates the token
            AuthModulePort.RefreshTokenCommand legitimateRefresh =
                new AuthModulePort.RefreshTokenCommand(
                    stolenToken, "127.0.0.1", "Chrome/Mac");
            authService.refreshTokens(legitimateRefresh).block();

            // Attacker uses the stolen (now rotated) token
            AuthModulePort.RefreshTokenCommand attackerRefresh =
                new AuthModulePort.RefreshTokenCommand(
                    stolenToken, "evil-ip", "Hacker/Bot");

            StepVerifier.create(authService.refreshTokens(attackerRefresh))
                    .expectErrorMatches(e ->
                        e.getMessage().equals("TOKEN_REUSE_DETECTED"))
                    .verify();

            // All tokens in the family must now be revoked
            StepVerifier.create(
                    refreshTokenRepository.findAll()
                        .filter(t -> t.getRevokedAt() == null)
                        .count())
                    .assertNext(activeCount ->
                        assertThat(activeCount).isEqualTo(0))
                    .verifyComplete();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Logout flow
    // ════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Logout flow")
    class LogoutFlow {

        @Test
        @DisplayName("Logout — token revoked in DB, deleted from Redis")
        void logout_revokesToken() {
            // Login
            AuthModulePort.TokenPairDto tokens =
                authService.processOAuth2Login(GOOGLE_LOGIN).block();

            // Logout
            AuthModulePort.LogoutCommand logoutCmd =
                new AuthModulePort.LogoutCommand(
                    tokens.userId(), tokens.refreshToken());

            StepVerifier.create(authService.logout(logoutCmd))
                    .verifyComplete();

            // Token must be revoked in DB
            StepVerifier.create(
                    refreshTokenRepository.findAll()
                        .filter(t -> "LOGOUT".equals(t.getRevokeReason())))
                    .assertNext(t -> {
                        assertThat(t.getRevokedAt()).isNotNull();
                        assertThat(t.getRevokeReason()).isEqualTo("LOGOUT");
                    })
                    .verifyComplete();

            // Trying to refresh after logout must fail
            AuthModulePort.RefreshTokenCommand refreshAfterLogout =
                new AuthModulePort.RefreshTokenCommand(
                    tokens.refreshToken(), "127.0.0.1", "Chrome/Mac");

            StepVerifier.create(authService.refreshTokens(refreshAfterLogout))
                    .expectErrorMatches(e ->
                        e.getMessage().equals("TOKEN_REUSE_DETECTED")
                        || e.getMessage().equals("INVALID_REFRESH_TOKEN"))
                    .verify();
        }

        @Test
        @DisplayName("Logout all — all sessions revoked across all devices")
        void logoutAll_revokesAllSessions() {
            // Login from two different devices
            authService.processOAuth2Login(GOOGLE_LOGIN).block();
            authService.processOAuth2Login(
                new AuthModulePort.OAuth2LoginCommand(
                    "google", "google-sub-123", "john@gmail.com",
                    "John Doe", "https://avatar.url",
                    "192.168.1.2", "Safari/iPhone"   // different device
                )).block();

            // Get userId
            AuthModulePort.TokenPairDto tokens =
                authService.processOAuth2Login(GOOGLE_LOGIN).block();

            // Logout all
            StepVerifier.create(authService.logoutAll(
                    new AuthModulePort.LogoutAllCommand(tokens.userId())))
                    .assertNext(count -> assertThat(count).isGreaterThan(0))
                    .verifyComplete();

            // No active tokens should remain
            StepVerifier.create(
                    refreshTokenRepository
                        .findByUserIdAndRevokedAtIsNull(tokens.userId())
                        .count())
                    .assertNext(count -> assertThat(count).isEqualTo(0))
                    .verifyComplete();
        }
    }
}
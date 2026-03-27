package com.smartgrocery.modules.auth.application;

import com.smartgrocery.modules.auth.api.AuthModulePort;
import com.smartgrocery.modules.auth.domain.RefreshToken;
import com.smartgrocery.modules.auth.domain.UserAccount;
import com.smartgrocery.modules.auth.domain.UserIdentity;
import com.smartgrocery.modules.auth.infrastructure.cache.RefreshTokenCacheRepository;
import com.smartgrocery.modules.auth.infrastructure.persistence.RefreshTokenRepository;
import com.smartgrocery.modules.auth.infrastructure.persistence.UserAccountRepository;
import com.smartgrocery.modules.auth.infrastructure.persistence.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserAccountRepository userAccountRepository;
  @Mock private UserIdentityRepository userIdentityRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private RefreshTokenCacheRepository refreshTokenCacheRepository;

  @InjectMocks private AuthService authService;

  // ── Shared test data ──────────────────────────────────────────────────
  private final UUID USER_ID = UUID.randomUUID();
  private final UUID FAMILY = UUID.randomUUID();
  private final String EMAIL = "john@gmail.com";

  private final AuthModulePort.OAuth2LoginCommand LOGIN_CMD =
      new AuthModulePort.OAuth2LoginCommand(
          "google", "g-789", EMAIL, "John Doe", "https://avatar.url", "127.0.0.1", "Chrome/Mac");

  private final AuthModulePort.RefreshTokenCommand REFRESH_CMD =
      new AuthModulePort.RefreshTokenCommand("raw-token-value", "127.0.0.1", "Chrome/Mac");

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        authService, "jwtSecret", "test-secret-key-must-be-at-least-32-characters-long");
    ReflectionTestUtils.setField(authService, "accessTokenExpiryMs", 900_000L);
    ReflectionTestUtils.setField(authService, "refreshTokenExpiryMs", 604_800_000L);

    // ── Default stubs — every mock returns safe Mono.empty() by default ──
    // This prevents NullPointerException when reactive chain is assembled.
    // Individual tests override these with specific return values.
    lenient()
        .when(userIdentityRepository.findByProviderAndProviderId(anyString(), anyString()))
        .thenReturn(Mono.empty());
    lenient().when(userAccountRepository.findByEmail(anyString())).thenReturn(Mono.empty());
    lenient().when(userAccountRepository.findById(any(UUID.class))).thenReturn(Mono.empty());
    lenient().when(userAccountRepository.save(any())).thenReturn(Mono.empty());
    lenient().when(userIdentityRepository.save(any())).thenReturn(Mono.empty());
    lenient().when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.empty());
    lenient().when(refreshTokenRepository.findById(any(UUID.class))).thenReturn(Mono.empty());
    lenient().when(refreshTokenRepository.save(any())).thenReturn(Mono.empty());
    lenient()
        .when(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(any()))
        .thenReturn(Flux.empty());
    lenient()
        .when(refreshTokenRepository.revokeAllByUserId(any(), any(), anyString()))
        .thenReturn(Mono.just(0));
    lenient()
        .when(refreshTokenRepository.revokeAllByFamily(any(), any(), anyString()))
        .thenReturn(Mono.just(0));
    lenient()
        .when(refreshTokenCacheRepository.findUserIdByTokenHash(anyString()))
        .thenReturn(Mono.empty());
    lenient()
        .when(refreshTokenCacheRepository.save(anyString(), anyString()))
        .thenReturn(Mono.empty());
    lenient().when(refreshTokenCacheRepository.delete(anyString())).thenReturn(Mono.empty());
    lenient()
        .when(refreshTokenCacheRepository.addToUserIndex(anyString(), anyString()))
        .thenReturn(Mono.empty());
    lenient()
        .when(refreshTokenCacheRepository.deleteAllByUserId(anyString()))
        .thenReturn(Mono.empty());
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  private UserAccount activeUser() {
    return UserAccount.builder()
        .id(USER_ID)
        .email(EMAIL)
        .status("ACTIVE")
        .failedLoginAttempts((short) 0)
        .onboardingStep("COMPLETED")
        .emailVerified(true)
        .build();
  }

  private UserIdentity googleIdentity() {
    return UserIdentity.builder()
        .id(UUID.randomUUID())
        .userId(USER_ID)
        .provider("google")
        .providerId("g-789")
        .isPrimary(true)
        .build();
  }

  private RefreshToken validToken() {
    return RefreshToken.builder()
        .id(UUID.randomUUID())
        .userId(USER_ID)
        .tokenHash("any-hash")
        .family(FAMILY)
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }

  private RefreshToken savedToken() {
    return RefreshToken.builder()
        .id(UUID.randomUUID())
        .userId(USER_ID)
        .tokenHash("saved-hash")
        .family(FAMILY)
        .expiresAt(Instant.now().plusSeconds(604800))
        .build();
  }

  // ════════════════════════════════════════════════════════════════════
  // processOAuth2Login
  // ════════════════════════════════════════════════════════════════════
  @Nested
  @DisplayName("processOAuth2Login")
  class ProcessOAuth2Login {

    @Test
    @DisplayName("Existing user — returns token pair without creating new account")
    void existingUser_returnsTokens() {
      when(userIdentityRepository.findByProviderAndProviderId("google", "g-789"))
          .thenReturn(Mono.just(googleIdentity()));
      when(userAccountRepository.findById(USER_ID)).thenReturn(Mono.just(activeUser()));
      when(userAccountRepository.save(any())).thenReturn(Mono.just(activeUser()));
      when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedToken()));

      StepVerifier.create(authService.processOAuth2Login(LOGIN_CMD))
          .assertNext(
              tokens -> {
                assert tokens.userId().equals(USER_ID);
                assert tokens.email().equals(EMAIL);
                assert tokens.accessToken() != null;
                assert tokens.refreshToken() != null;
              })
          .verifyComplete();

      // New account must NOT have been created
      verify(userAccountRepository, never()).save(argThat(u -> u.getId() == null));
    }

    @Test
    @DisplayName("Brand new user — creates account, identity, returns token pair")
    void newUser_createsAccountAndReturnsTokens() {
      // No existing identity, no existing email
      when(userIdentityRepository.findByProviderAndProviderId("google", "g-789"))
          .thenReturn(Mono.empty());
      when(userAccountRepository.findByEmail(EMAIL)).thenReturn(Mono.empty());
      when(userAccountRepository.save(any())).thenReturn(Mono.just(activeUser()));
      when(userIdentityRepository.save(any())).thenReturn(Mono.just(googleIdentity()));
      when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedToken()));

      StepVerifier.create(authService.processOAuth2Login(LOGIN_CMD))
          .assertNext(
              tokens -> {
                assert tokens.userId().equals(USER_ID);
                assert tokens.accessToken() != null;
              })
          .verifyComplete();

      verify(userIdentityRepository).save(any());
    }

    @Test
    @DisplayName("Locked account — returns ACCOUNT_LOCKED error, no tokens issued")
    void lockedAccount_returnsError() {
      UserAccount lockedAccount =
          UserAccount.builder()
              .id(USER_ID)
              .email(EMAIL)
              .status("LOCKED")
              .failedLoginAttempts((short) 5)
              .build();

      when(userIdentityRepository.findByProviderAndProviderId("google", "g-789"))
          .thenReturn(Mono.just(googleIdentity()));
      when(userAccountRepository.findById(USER_ID)).thenReturn(Mono.just(lockedAccount));

      StepVerifier.create(authService.processOAuth2Login(LOGIN_CMD))
          .expectErrorMatches(e -> e.getMessage().equals("ACCOUNT_LOCKED"))
          .verify();

      verify(refreshTokenRepository, never()).save(any());
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // refreshTokens
  // ════════════════════════════════════════════════════════════════════
  @Nested
  @DisplayName("refreshTokens")
  class RefreshTokens {

    @Test
    @DisplayName("Valid token — rotates and returns new token pair")
    void validToken_returnsNewPair() {
      when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.just(validToken()));
      when(refreshTokenRepository.save(any())).thenReturn(Mono.just(validToken()));
      when(userAccountRepository.findById(USER_ID)).thenReturn(Mono.just(activeUser()));
      when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedToken()));

      StepVerifier.create(authService.refreshTokens(REFRESH_CMD))
          .assertNext(
              tokens -> {
                assert tokens.accessToken() != null;
                assert tokens.refreshToken() != null;
              })
          .verifyComplete();
    }

    @Test
    @DisplayName("Revoked token — triggers reuse detection, returns error")
    void revokedToken_triggersReuseDetection() {
      RefreshToken revokedToken =
          RefreshToken.builder()
              .id(UUID.randomUUID())
              .userId(USER_ID)
              .tokenHash("any-hash")
              .family(FAMILY)
              .expiresAt(Instant.now().plusSeconds(3600))
              .revokedAt(Instant.now().minusSeconds(60))
              .revokeReason("ROTATED")
              .build();

      when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.just(revokedToken));
      when(refreshTokenRepository.revokeAllByFamily(eq(FAMILY), any(), eq("REUSE_DETECTED")))
          .thenReturn(Mono.just(2));

      StepVerifier.create(authService.refreshTokens(REFRESH_CMD))
          .expectErrorMatches(e -> e.getMessage().equals("TOKEN_REUSE_DETECTED"))
          .verify();

      verify(refreshTokenRepository).revokeAllByFamily(eq(FAMILY), any(), eq("REUSE_DETECTED"));
    }

    @Test
    @DisplayName("Expired token — returns TOKEN_EXPIRED error")
    void expiredToken_returnsError() {
      RefreshToken expiredToken =
          RefreshToken.builder()
              .id(UUID.randomUUID())
              .userId(USER_ID)
              .tokenHash("any-hash")
              .family(FAMILY)
              .expiresAt(Instant.now().minusSeconds(60))
              .build();

      when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.just(expiredToken));

      StepVerifier.create(authService.refreshTokens(REFRESH_CMD))
          .expectErrorMatches(e -> e.getMessage().equals("TOKEN_EXPIRED"))
          .verify();
    }

    @Test
    @DisplayName("Token not found — returns INVALID_REFRESH_TOKEN error")
    void tokenNotFound_returnsError() {
      // Default stubs already return Mono.empty() for all lookups
      StepVerifier.create(authService.refreshTokens(REFRESH_CMD))
          .expectErrorMatches(e -> e.getMessage().equals("INVALID_REFRESH_TOKEN"))
          .verify();
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // logout
  // ════════════════════════════════════════════════════════════════════
  @Nested
  @DisplayName("logout")
  class Logout {

    @Test
    @DisplayName("Valid token — revokes in DB and deletes from Redis")
    void validToken_revokesEverywhere() {
      when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.just(validToken()));
      when(refreshTokenRepository.save(any())).thenReturn(Mono.just(validToken()));

      AuthModulePort.LogoutCommand cmd = new AuthModulePort.LogoutCommand(USER_ID, "raw-token");

      StepVerifier.create(authService.logout(cmd)).verifyComplete();

      verify(refreshTokenRepository)
          .save(argThat(t -> t.getRevokedAt() != null && "LOGOUT".equals(t.getRevokeReason())));
      verify(refreshTokenCacheRepository).delete(anyString());
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // revokeSession
  // ════════════════════════════════════════════════════════════════════
  @Nested
  @DisplayName("revokeSession")
  class RevokeSession {

    @Test
    @DisplayName("Session belongs to different user — returns UNAUTHORIZED")
    void wrongUser_returnsUnauthorized() {
      UUID otherUserId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();

      RefreshToken tokenOwnedByOther =
          RefreshToken.builder()
              .id(sessionId)
              .userId(otherUserId) // owned by someone else
              .tokenHash("hash")
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();

      when(refreshTokenRepository.findById(sessionId)).thenReturn(Mono.just(tokenOwnedByOther));

      StepVerifier.create(authService.revokeSession(USER_ID, sessionId))
          .expectErrorMatches(e -> e.getMessage().equals("UNAUTHORIZED"))
          .verify();

      verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Session not found — returns SESSION_NOT_FOUND error")
    void sessionNotFound_returnsError() {
      StepVerifier.create(authService.revokeSession(USER_ID, UUID.randomUUID()))
          .expectErrorMatches(e -> e.getMessage().equals("SESSION_NOT_FOUND"))
          .verify();
    }
  }
}

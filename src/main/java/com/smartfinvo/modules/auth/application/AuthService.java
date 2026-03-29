package com.smartfinvo.modules.auth.application;

import com.smartfinvo.modules.auth.api.AuthModulePort;
import com.smartfinvo.modules.auth.domain.RefreshToken;
import com.smartfinvo.modules.auth.domain.UserAccount;
import com.smartfinvo.modules.auth.domain.UserIdentity;
import com.smartfinvo.modules.auth.infrastructure.cache.RefreshTokenCacheRepository;
import com.smartfinvo.modules.auth.infrastructure.persistence.RefreshTokenRepository;
import com.smartfinvo.modules.auth.infrastructure.persistence.UserAccountRepository;
import com.smartfinvo.modules.auth.infrastructure.persistence.UserIdentityRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthModulePort {

  private final UserAccountRepository userAccountRepository;
  private final UserIdentityRepository userIdentityRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenCacheRepository refreshTokenCacheRepository;

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @Value("${app.jwt.access-token-expiry-ms}")
  private long accessTokenExpiryMs;

  @Value("${app.jwt.refresh-token-expiry-ms}")
  private long refreshTokenExpiryMs;

  @Override
  @Transactional("connectionFactoryTransactionManager")
  public Mono<TokenPairDto> processOAuth2Login(OAuth2LoginCommand cmd) {
    log.info(
        "Processing OAuth2 login provider={} email={}", cmd.provider(), maskEmail(cmd.email()));

    return userIdentityRepository
        .findByProviderAndProviderId(cmd.provider(), cmd.providerId())
        .flatMap(
            identity -> {
              // Existing user — load their account
              log.debug("Existing identity found userId={}", identity.getUserId());
              return userAccountRepository.findById(identity.getUserId());
            })
        .switchIfEmpty(
            // No identity found — new user or new provider for existing email
                Mono.defer(() -> handleNewOAuth2User(cmd)))
        .flatMap(
            user -> {
              // Check account is allowed to login
              if (user.isLocked()) {
                log.warn("Login attempt on locked account userId={}", user.getId());
                return Mono.error(new RuntimeException("ACCOUNT_LOCKED"));
              }
              if (!user.isActive()) {
                log.warn("Login attempt on inactive account userId={}", user.getId());
                return Mono.error(new RuntimeException("ACCOUNT_SUSPENDED"));
              }
              // Reset failed attempts on successful login
              user.resetFailedAttempts();
              return userAccountRepository
                  .save(user)
                  .flatMap(
                      savedUser -> issueTokenPair(savedUser, cmd.ipAddress(), cmd.deviceHint()));
            })
        .doOnSuccess(
            tokens ->
                log.info(
                    "OAuth2 login success provider={} userId={}", cmd.provider(), tokens.userId()))
        .doOnError(
            error ->
                log.error(
                    "OAuth2 login failed provider={} error={}",
                    cmd.provider(),
                    error.getMessage()));
  }
  // ── Refresh Tokens ─────────────────────────────────────────────────────
  // Client sends refresh token → we validate → issue new pair
  // Redis checked first → PostgreSQL as fallback
  @Override
  @Transactional("connectionFactoryTransactionManager")
  public Mono<TokenPairDto> refreshTokens(RefreshTokenCommand cmd) {
    String tokenHash = hashToken(cmd.rawRefreshToken());

    return refreshTokenCacheRepository
            .findUserIdByTokenHash(tokenHash)
            .flatMap(userId -> {
              // Cache hit — load full token from DB for validation
              log.debug("Token cache hit userId={}", userId);
              return refreshTokenRepository.findByTokenHash(tokenHash);
            })
            .switchIfEmpty(
                    // Cache miss — load directly from DB
                    refreshTokenRepository.findByTokenHash(tokenHash)
                            .doOnNext(t -> log.debug("Token cache miss — loaded from DB"))
            )
            .switchIfEmpty(
                    Mono.error(new RuntimeException("INVALID_REFRESH_TOKEN"))
            )
            .flatMap(existingToken -> {
              // Token found — now validate it
              if (existingToken.isRevoked()) {
                // SECURITY: Revoked token used — token theft detected
                // Revoke entire family immediately
                log.warn("TOKEN REUSE DETECTED userId={} family={}",
                        existingToken.getUserId(), existingToken.getFamily());
                return handleTokenReuse(existingToken)
                        .then(Mono.error(new RuntimeException("TOKEN_REUSE_DETECTED")));
              }
              if (existingToken.isExpired()) {
                return Mono.error(new RuntimeException("TOKEN_EXPIRED"));
              }
              // Valid token — revoke old one and issue new pair
              return rotateToken(existingToken, cmd.ipAddress(), cmd.deviceHint());
            });
  }

  // ── Logout ─────────────────────────────────────────────────────────────
  @Override
  @Transactional("connectionFactoryTransactionManager")
  public Mono<Void> logout(LogoutCommand cmd) {
    String tokenHash = hashToken(cmd.rawRefreshToken());
    Instant now = Instant.now();

    return refreshTokenRepository.findByTokenHash(tokenHash)
            .flatMap(token -> {
              token.revoke("LOGOUT");
              return refreshTokenRepository.save(token)
                      .then(refreshTokenCacheRepository.delete(tokenHash));
            })
            .doOnSuccess(v ->
                    log.info("Logout success userId={}", cmd.userId()))
            .then();
  }

  // ── Logout All Devices ─────────────────────────────────────────────────
  @Override
  @Transactional("connectionFactoryTransactionManager")
  public Mono<Integer> logoutAll(LogoutAllCommand cmd) {
    return refreshTokenRepository
            .revokeAllByUserId(cmd.userId(), Instant.now(), "LOGOUT_ALL")
            .flatMap(count -> refreshTokenCacheRepository
                    .deleteAllByUserId(cmd.userId().toString())
                    .thenReturn(count))
            .doOnSuccess(count ->
                    log.info("All sessions revoked userId={} count={}", cmd.userId(), count));
  }

  // ── Get Current User ───────────────────────────────────────────────────
  @Override
  public Mono<AuthUserDto> getCurrentUser(UUID userId) {
    return userAccountRepository.findById(userId)
            .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
            .flatMap(user -> userIdentityRepository
                    .findByUserIdAndIsPrimaryTrue(userId)
                    .map(identity -> new AuthUserDto(
                            user.getId(),
                            user.getEmail(),
                            user.getEmailVerified(),
                            identity.getProvider(),
                            user.getOnboardingStep(),
                            user.getDisplayName(),
                            user.getAvatarUrl()
                    ))
            );
  }

  // ── Get Active Sessions ────────────────────────────────────────────────
  @Override
  public Flux<SessionDto> getActiveSessions(UUID userId) {
    return refreshTokenRepository
            .findByUserIdAndRevokedAtIsNull(userId)
            .map(token -> new SessionDto(
                    token.getId(),
                    token.getDeviceHint(),
                    token.getIpAddress(),
                    token.getCreatedAt().toString(),
                    token.getExpiresAt().toString(),
                    false // isCurrent — set in controller based on request token
            ));
  }

  // ── Revoke Session ─────────────────────────────────────────────────────
  @Override
  @Transactional("connectionFactoryTransactionManager")
  public Mono<Void> revokeSession(UUID userId, UUID sessionId) {
    return refreshTokenRepository.findById(sessionId)
            .switchIfEmpty(Mono.error(new RuntimeException("SESSION_NOT_FOUND")))
            .flatMap(token -> {
              // Ensure session belongs to requesting user
              if (!token.getUserId().equals(userId)) {
                return Mono.error(new RuntimeException("UNAUTHORIZED"));
              }
              token.revoke("USER_REVOKED");
              return refreshTokenRepository.save(token)
                      .then(refreshTokenCacheRepository.delete(token.getTokenHash()));
            })
            .doOnSuccess(v ->
                    log.info("Session revoked userId={} sessionId={}", userId, sessionId))
            .then();
  }






  // Handle first time login for a new user or new provider
  private Mono<UserAccount> handleNewOAuth2User(OAuth2LoginCommand cmd) {
    return userAccountRepository
            .findByEmail(cmd.email())
            .flatMap(
                    existingUser -> {
                      // User exists with different provider — link new provider
                      log.info(
                              "Linking new provider={} to existing userId={}",
                              cmd.provider(),
                              existingUser.getId());
                      return saveUserIdentity(existingUser.getId(), cmd, false).thenReturn(existingUser);
                    })
            .switchIfEmpty(
                    // Brand new user — create account and identity
                    createNewUser(cmd));
  }

  // Save OAuth2 provider identity
  private Mono<UserIdentity> saveUserIdentity(
          UUID userId, OAuth2LoginCommand cmd, boolean isPrimary) {
    UserIdentity identity =
            UserIdentity.builder()
                    .userId(userId)
                    .provider(cmd.provider())
                    .providerId(cmd.providerId())
                    .providerEmail(cmd.email())
                    .providerName(cmd.displayName())
                    .avatarUrl(cmd.avatarUrl())
                    .isPrimary(isPrimary)
                    .build();
    return userIdentityRepository.save(identity);
  }

  // Create new UserAccount and UserIdentity in one transaction
  private Mono<UserAccount> createNewUser(OAuth2LoginCommand cmd) {
    UserAccount newUser =
            UserAccount.builder()
                    .email(cmd.email())
                    .emailVerified(true) // OAuth2 emails are pre-verified
                    .displayName(cmd.displayName())
                    .avatarUrl(cmd.avatarUrl())
                    .status("ACTIVE")
                    .failedLoginAttempts((short) 0)
                    .onboardingStep("ACCOUNT_CREATED")
                    .build();

    return userAccountRepository
            .save(newUser)
            .flatMap(
                    savedUser -> {
                      log.info(
                              "New user created userId={} email={}",
                              savedUser.getId(),
                              maskEmail(savedUser.getEmail()));
                      return saveUserIdentity(savedUser.getId(), cmd, true).thenReturn(savedUser);
                    });
  }

  // Generate access token + refresh token and save both
  private Mono<TokenPairDto> issueTokenPair(
          UserAccount user, String ipAddress, String deviceHint) {
    String accessToken = generateAccessToken(user);
    String rawRefreshToken = UUID.randomUUID().toString();
    String tokenHash = hashToken(rawRefreshToken);
    UUID family = UUID.randomUUID();
    Instant expiresAt = Instant.now().plusMillis(refreshTokenExpiryMs);

    RefreshToken refreshToken = RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(tokenHash)
            .family(family)
            .deviceHint(deviceHint)
            .ipAddress(ipAddress)
            .expiresAt(expiresAt)
            .build();

    // Save to PostgreSQL and Redis simultaneously using zip
    return Mono.zip(
            refreshTokenRepository.save(refreshToken),
            refreshTokenCacheRepository.save(tokenHash, user.getId().toString())
                    .then(refreshTokenCacheRepository
                            .addToUserIndex(user.getId().toString(), tokenHash))
                    .thenReturn(true)
    ).map(tuple -> new TokenPairDto(
            accessToken,
            rawRefreshToken,      // raw token sent to client once
            user.getId(),
            user.getEmail(),
            user.getOnboardingStep(),
            accessTokenExpiryMs / 1000
    ));
  }

  // SHA-256 hash of raw token — never store raw tokens
  private String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(
              rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      throw new RuntimeException("Failed to hash token", e);
    }
  }

  // Generate JWT access token — signed with HS512
  private String generateAccessToken(UserAccount user) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(accessTokenExpiryMs);

    return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("onboardingStep", user.getOnboardingStep())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(Keys.hmacShaKeyFor(
                    jwtSecret.getBytes(StandardCharsets.UTF_8)))
            .compact();
  }

  // Mask email for logs — never log full email
  // user@example.com → u***@example.com
  private String maskEmail(String email) {
    if (email == null || !email.contains("@")) return "***";
    String[] parts = email.split("@");
    return parts[0].charAt(0) + "***@" + parts[1];
  }


  // Revoke entire token family — called when reuse is detected
  private Mono<Void> handleTokenReuse(RefreshToken token) {
    return refreshTokenRepository
            .revokeAllByFamily(token.getFamily(), Instant.now(), "REUSE_DETECTED")
            .then(refreshTokenCacheRepository
                    .deleteAllByUserId(token.getUserId().toString()))
            .doOnSuccess(v ->
                    log.warn("Token family revoked due to reuse family={} userId={}",
                            token.getFamily(), token.getUserId()));
  }

  // Revoke old token and issue new pair — called on every refresh
  private Mono<TokenPairDto> rotateToken(
          RefreshToken oldToken, String ipAddress, String deviceHint) {
    oldToken.revoke("ROTATED");

    return refreshTokenRepository.save(oldToken)
            .then(refreshTokenCacheRepository.delete(oldToken.getTokenHash()))
            .then(userAccountRepository.findById(oldToken.getUserId()))
            .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
            .flatMap(user -> issueTokenPair(user, ipAddress, deviceHint));
  }

}

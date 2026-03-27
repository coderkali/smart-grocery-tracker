package com.smartgrocery.modules.auth.infrastructure.cache;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;

// This is our Redis layer for refresh tokens
// It sits in front of PostgreSQL — checked first on every request
@Slf4j
@Repository
@RequiredArgsConstructor
public class RefreshTokenCacheRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // All keys are prefixed to avoid collisions with other data in Redis
    // Key format: "refresh_token:{tokenHash}" → value: "{userId}"
    private static final String KEY_PREFIX = "refresh_token:";

    // How long token lives in Redis — must match DB expiry (7 days)
    private static final Duration TOKEN_TTL = Duration.ofDays(7);


    public Mono<Void> save(String tokenHash, String userId) {
        String key = KEY_PREFIX + tokenHash;
        return redisTemplate.opsForValue().set(key, userId, TOKEN_TTL).doOnSuccess(value -> {
            log.info("Saved refresh token to Redis: key={}, userId={}", key, userId);
        }).doOnError(error -> {
            log.error("Failed to save refresh token to Redis: key={}, userId={}, error={}", key, userId, error.getMessage());
        }).then();
    }


    // Look up userId by token hash
    // Returns Mono.empty() if not found — caller falls back to PostgreSQL
    public Mono<String> findUserIdByTokenHash(String tokenHash) {
        String key = KEY_PREFIX + tokenHash;
        return redisTemplate.opsForValue()
                .get(key)
                .doOnNext(userId ->
                        log.debug("Refresh token cache hit for userId={}", userId));
    }

    // Delete token from Redis — called on logout or reuse detection
    public Mono<Void> delete(String tokenHash) {
        String key = KEY_PREFIX + tokenHash;
        return redisTemplate.delete(key)
                .doOnSuccess(count ->
                        log.debug("Refresh token removed from cache, hash={}", tokenHash))
                .then();
    }

    // Delete all tokens for a user — called on logout all devices
    // Uses Redis SCAN to find all keys matching the pattern
    // We store a reverse index: "user_tokens:{userId}" → Set of tokenHashes
    public Mono<Void> deleteAllByUserId(String userId) {
        String indexKey = "user_tokens:" + userId;
        return redisTemplate.opsForSet()
                .members(indexKey)
                .flatMap(this::delete)
                .then(redisTemplate.delete(indexKey))
                .doOnSuccess(v ->
                        log.debug("All cached tokens removed for userId={}", userId))
                .then();
    }

    // Add token to user's token set — for logout all devices support
    public Mono<Void> addToUserIndex(String userId, String tokenHash) {
        String indexKey = "user_tokens:" + userId;
        return redisTemplate.opsForSet()
                .add(indexKey, tokenHash)
                .then(redisTemplate.expire(indexKey, TOKEN_TTL))
                .then();
    }
}



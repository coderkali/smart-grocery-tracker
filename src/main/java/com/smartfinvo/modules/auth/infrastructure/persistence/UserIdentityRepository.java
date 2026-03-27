package com.smartfinvo.modules.auth.infrastructure.persistence;

import com.smartfinvo.modules.auth.domain.UserIdentity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface UserIdentityRepository
        extends ReactiveCrudRepository<UserIdentity, UUID> {

    // Find identity by provider + providerId combination
    // Used during OAuth2 login to check if this provider account
    // is already linked to an existing user
    // SELECT * FROM user_identity WHERE provider = ? AND provider_id = ?
    Mono<UserIdentity> findByProviderAndProviderId(String provider, String providerId);

    // Find all identities for a user
    // Used in "connected accounts" screen — shows Google, GitHub linked
    // SELECT * FROM user_identity WHERE user_id = ?
    Flux<UserIdentity> findByUserId(UUID userId);

    // Find primary identity for a user
    // Used to show which provider the user originally signed up with
    // SELECT * FROM user_identity WHERE user_id = ? AND is_primary = true
    Mono<UserIdentity> findByUserIdAndIsPrimaryTrue(UUID userId);

    // Check if a specific provider is already linked to a user
    // Used to prevent duplicate linking
    // SELECT COUNT(*) FROM user_identity WHERE user_id = ? AND provider = ?
    Mono<Boolean> existsByUserIdAndProvider(UUID userId, String provider);
}
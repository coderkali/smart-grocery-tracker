package com.smartfinvo.modules.auth.infrastructure.persistence;

import com.smartfinvo.modules.auth.domain.UserAccount;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;


// ReactiveCrudRepository gives us basic CRUD operations for free
// All methods return Mono or Flux — never blocking types like Optional or List
//
// Built-in methods we get automatically:
//   save(entity)        → Mono<UserAccount>
//   findById(id)        → Mono<UserAccount>
//   deleteById(id)      → Mono<Void>
//   existsById(id)      → Mono<Boolean>

@Repository
public interface UserAccountRepository extends ReactiveCrudRepository<UserAccount, UUID> {


    //Find user by email — used during OAuth2 login
    // Spring Data generates the SQL automatically from the method name:
    // SELECT * FROM user_account WHERE email = ?
    Mono<UserAccount> findByEmail(String email);

    // Check if email already exists — used during registration
    // SELECT COUNT(*) FROM user_account WHERE email = ?
    Mono<Boolean> existsByEmail(String email);

    // Find active user by email — used for login validation
    // Spring Data reads method name: findBy + Email + And + Status
    // SELECT * FROM user_account WHERE email = ? AND status = ?
    Mono<UserAccount> findByEmailAndStatus(String email, String status);



}

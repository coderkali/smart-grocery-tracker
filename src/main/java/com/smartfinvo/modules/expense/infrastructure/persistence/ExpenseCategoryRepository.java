package com.smartfinvo.modules.expense.infrastructure.persistence;

import com.smartfinvo.modules.expense.domain.ExpenseCategory;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ExpenseCategoryRepository extends R2dbcRepository<ExpenseCategory, UUID> {

    /**
     * Find all active categories for a user
     */
    Flux<ExpenseCategory> findByUserIdAndIsActiveTrueOrderByNameAsc(UUID userId);

    /**
     * Find category by ID and user ID
     */
    Mono<ExpenseCategory> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Find category by name and user ID
     */
    Mono<ExpenseCategory> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    /**
     * Check if category exists for user
     */
    Mono<Boolean> existsByIdAndUserId(UUID id, UUID userId);

    /**
     * Count active categories for user
     */
    Mono<Long> countByUserIdAndIsActiveTrue(UUID userId);
}

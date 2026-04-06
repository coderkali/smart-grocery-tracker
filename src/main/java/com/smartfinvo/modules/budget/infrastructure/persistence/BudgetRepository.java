package com.smartfinvo.modules.budget.infrastructure.persistence;

import com.smartfinvo.modules.budget.domain.Budget;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface BudgetRepository extends R2dbcRepository<Budget, UUID> {

    // All active (non-deleted) budgets for a user — used by GET /budgets
    Flux<Budget> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    // Single budget scoped to a user — 404 if it belongs to another user
    Mono<Budget> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    // Filter by category — used by the analytics budget-vs-actual endpoint
    Flux<Budget> findByUserIdAndCategoryIdAndDeletedAtIsNull(UUID userId, UUID categoryId);

    // Soft-delete by ID and userId in one statement — avoids load-then-save for simple deletes
    @Query("""
            UPDATE budget
            SET deleted_at = NOW(), updated_at = NOW()
            WHERE id = :id
            AND user_id = :userId
            AND deleted_at IS NULL
            """)
    Mono<Integer> softDeleteByIdAndUserId(UUID id, UUID userId);
}

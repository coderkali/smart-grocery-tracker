package com.smartfinvo.modules.expense.infrastructure.persistence;

import com.smartfinvo.modules.expense.domain.Expense;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends R2dbcRepository<Expense, UUID> {

    /**
     * Find all expenses for a user (not deleted)
     */
    Flux<Expense> findByUserIdAndDeletedAtIsNullOrderByExpenseDateDesc(UUID userId);

    /**
     * Find single expense by ID and user ID
     */
    Mono<Expense> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /**
     * Find expenses by user and category
     */
    Flux<Expense> findByUserIdAndCategoryIdAndDeletedAtIsNullOrderByExpenseDateDesc(UUID userId, UUID categoryId);

    /**
     * Find expenses within a date range for a user
     */
    @Query("""
            SELECT * FROM expense
            WHERE user_id = :userId
            AND expense_date >= :startDate
            AND expense_date <= :endDate
            AND deleted_at IS NULL
            ORDER BY expense_date DESC
            """)
    Flux<Expense> findByUserIdAndDateRange(UUID userId, LocalDate startDate, LocalDate endDate);

    /**
     * Count expenses for a user (for pagination)
     */
    Mono<Long> countByUserIdAndDeletedAtIsNull(UUID userId);

    /**
     * Find expenses by category for a user
     */
    Mono<Long> countByUserIdAndCategoryIdAndDeletedAtIsNull(UUID userId, UUID categoryId);

    /**
     * Delete expense by marking deleted_at (soft delete)
     */
    @Query("""
            UPDATE expense
            SET deleted_at = NOW()
            WHERE id = :id
            AND user_id = :userId
            """)
    Mono<Void> softDeleteByIdAndUserId(UUID id, UUID userId);
}

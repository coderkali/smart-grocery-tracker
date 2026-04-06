package com.smartfinvo.modules.expense.infrastructure.persistence;

import com.smartfinvo.modules.expense.domain.Expense;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
     * Sum expense amounts for a user in a category within a date range.
     * Used by BudgetService to calculate currentSpent for each budget.
     * COALESCE ensures 0 is returned when there are no matching rows (not null).
     */
    @Query("""
            SELECT COALESCE(SUM(amount), 0) FROM expense
            WHERE user_id    = :userId
            AND   category_id = :categoryId
            AND   expense_date >= :startDate
            AND   expense_date <= :endDate
            AND   deleted_at IS NULL
            """)
    Mono<BigDecimal> sumAmountByUserIdAndCategoryIdAndDateRange(
            UUID userId, UUID categoryId, LocalDate startDate, LocalDate endDate);

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

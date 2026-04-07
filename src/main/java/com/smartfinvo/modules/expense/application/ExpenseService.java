package com.smartfinvo.modules.expense.application;

import com.smartfinvo.modules.expense.domain.Expense;
import com.smartfinvo.modules.expense.infrastructure.persistence.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.smartfinvo.modules.expense.infrastructure.web.dto.BulkCreateExpenseRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    /**
     * Create a new expense
     */
    @Transactional("connectionFactoryTransactionManager")
    public Mono<Expense> createExpense(UUID userId, Expense expense) {
        log.info("Creating expense for user: {}", userId);

        // Set defaults — do NOT set id; R2DBC needs id=null to treat this as INSERT
        expense.setUserId(userId);
        expense.setCreatedAt(OffsetDateTime.now());
        expense.setUpdatedAt(OffsetDateTime.now());
        expense.setVersion(0);

        // Validate
        if (expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException("Amount must be greater than 0"));
        }

        if (expense.getExpenseDate().isAfter(LocalDate.now())) {
            return Mono.error(new IllegalArgumentException("Expense date cannot be in the future"));
        }

        return expenseRepository.save(expense)
                .doOnSuccess(saved -> log.info("Expense created: {}", saved.getId()))
                .doOnError(err -> log.error("Failed to create expense", err));
    }

    /**
     * Get expense by ID
     */
    public Mono<Expense> getExpenseById(UUID expenseId, UUID userId) {
        log.info("Fetching expense: {} for user: {}", expenseId, userId);

        return expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(expenseId, userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Expense not found")))
                .doOnError(err -> log.error("Failed to fetch expense", err));
    }

    /**
     * Get all expenses for a user (paginated via caller)
     */
    public Flux<Expense> getAllExpenses(UUID userId) {
        log.info("Fetching all expenses for user: {}", userId);

        return expenseRepository.findByUserIdAndDeletedAtIsNullOrderByExpenseDateDesc(userId)
                .doOnError(err -> log.error("Failed to fetch expenses", err));
    }

    /**
     * Get expenses for a user in a date range
     */
    public Flux<Expense> getExpensesByDateRange(UUID userId, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching expenses for user: {} between {} and {}", userId, startDate, endDate);

        return expenseRepository.findByUserIdAndDateRange(userId, startDate, endDate)
                .doOnError(err -> log.error("Failed to fetch expenses by date range", err));
    }

    /**
     * Get expenses by category
     */
    public Flux<Expense> getExpensesByCategory(UUID userId, UUID categoryId) {
        log.info("Fetching expenses for user: {} in category: {}", userId, categoryId);

        return expenseRepository.findByUserIdAndCategoryIdAndDeletedAtIsNullOrderByExpenseDateDesc(userId, categoryId)
                .doOnError(err -> log.error("Failed to fetch expenses by category", err));
    }

    /**
     * Update an expense
     */
    @Transactional("connectionFactoryTransactionManager")
    public Mono<Expense> updateExpense(UUID expenseId, UUID userId, Expense updateData) {
        log.info("Updating expense: {} for user: {}", expenseId, userId);

        return getExpenseById(expenseId, userId)
                .flatMap(existing -> {
                    // Update fields
                    if (updateData.getAmount() != null) {
                        existing.setAmount(updateData.getAmount());
                    }
                    if (updateData.getDescription() != null) {
                        existing.setDescription(updateData.getDescription());
                    }
                    if (updateData.getCategoryId() != null) {
                        existing.setCategoryId(updateData.getCategoryId());
                    }
                    if (updateData.getExpenseDate() != null) {
                        existing.setExpenseDate(updateData.getExpenseDate());
                    }
                    if (updateData.getPaymentMethod() != null) {
                        existing.setPaymentMethod(updateData.getPaymentMethod());
                    }
                    if (updateData.getTags() != null) {
                        existing.setTags(updateData.getTags());
                    }
                    if (updateData.getNotes() != null) {
                        existing.setNotes(updateData.getNotes());
                    }

                    existing.setUpdatedAt(OffsetDateTime.now());

                    return expenseRepository.save(existing)
                            .doOnSuccess(saved -> log.info("Expense updated: {}", saved.getId()));
                })
                .doOnError(err -> log.error("Failed to update expense", err));
    }

    /**
     * Delete expense (soft delete)
     */
    @Transactional("connectionFactoryTransactionManager")
    public Mono<Void> deleteExpense(UUID expenseId, UUID userId) {
        log.info("Deleting expense: {} for user: {}", expenseId, userId);

        return getExpenseById(expenseId, userId)
                .flatMap(expense -> {
                    expense.softDelete();
                    return expenseRepository.save(expense).then();
                })
                .doOnSuccess(v -> log.info("Expense deleted: {}", expenseId))
                .doOnError(err -> log.error("Failed to delete expense", err));
    }

    /**
     * Bulk-create multiple expenses in a single transaction.
     * All items share the same receiptDate, vendor, and receiptImageUrl from the request.
     * Uses saveAll() which issues one INSERT per row inside the same transaction —
     * if any row fails validation or DB constraint, the whole batch is rolled back.
     * Returns the saved entities collected into a list so the controller can build
     * BulkExpenseResponse with a count and the full list of created records.
     */
    @Transactional("connectionFactoryTransactionManager")
    public Mono<List<Expense>> bulkCreateExpenses(UUID userId, BulkCreateExpenseRequest request) {
        log.info("Bulk creating {} expenses for userId={}", request.getItems().size(), userId);

        // Build one Expense entity per item — shared fields (date, vendor, receipt) applied to all
        List<Expense> expenses = request.getItems().stream()
                .map(item -> Expense.builder()
                        .userId(userId)
                        .categoryId(item.getCategoryId())
                        .amount(item.getAmount())
                        .currency(item.getCurrency() != null ? item.getCurrency() : "USD")
                        .description(item.getDescription())
                        .expenseDate(request.getReceiptDate())
                        // vendor stored in paymentMethod field — closest available column
                        .paymentMethod(request.getVendor())
                        .receiptUrl(request.getReceiptImageUrl())
                        .version(0)
                        .createdAt(OffsetDateTime.now())
                        .updatedAt(OffsetDateTime.now())
                        .build())
                .toList();

        // saveAll() is inherited from ReactiveCrudRepository — runs inside the @Transactional boundary
        // collectList() collects the Flux<Expense> into Mono<List<Expense>> for the response
        return expenseRepository.saveAll(expenses)
                .collectList()
                .doOnSuccess(saved -> log.info("Bulk created {} expenses for userId={}", saved.size(), userId))
                .doOnError(err -> log.error("Bulk create failed for userId={} error={}", userId, err.getMessage()));
    }

    /**
     * Sum expense amounts for a category within a date range.
     * Called by BudgetService to compute currentSpent for each budget.
     * Returns BigDecimal.ZERO if no expenses match (defaultIfEmpty handles null from COALESCE).
     */
    public Mono<BigDecimal> sumByCategoryAndDateRange(
            UUID userId, UUID categoryId, LocalDate startDate, LocalDate endDate) {
        log.debug("Summing expenses userId={} categoryId={} {} to {}", userId, categoryId, startDate, endDate);
        return expenseRepository
                .sumAmountByUserIdAndCategoryIdAndDateRange(userId, categoryId, startDate, endDate)
                .map(sum -> Objects.requireNonNullElse(sum, BigDecimal.ZERO))
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * Get total expenses for a user
     */
    public Mono<Long> countUserExpenses(UUID userId) {
        log.info("Counting expenses for user: {}", userId);

        return expenseRepository.countByUserIdAndDeletedAtIsNull(userId)
                .doOnError(err -> log.error("Failed to count expenses", err));
    }

    /**
     * Get total expenses in a category for a user
     */
    public Mono<Long> countExpensesByCategory(UUID userId, UUID categoryId) {
        log.info("Counting expenses for user: {} in category: {}", userId, categoryId);

        return expenseRepository.countByUserIdAndCategoryIdAndDeletedAtIsNull(userId, categoryId)
                .doOnError(err -> log.error("Failed to count expenses by category", err));
    }
}

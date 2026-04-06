package com.smartfinvo.modules.budget.application;

import com.smartfinvo.modules.budget.domain.Budget;
import com.smartfinvo.modules.budget.infrastructure.persistence.BudgetRepository;
import com.smartfinvo.modules.budget.infrastructure.web.dto.BudgetResponse;
import com.smartfinvo.modules.budget.infrastructure.web.dto.CreateBudgetRequest;
import com.smartfinvo.modules.budget.infrastructure.web.dto.UpdateBudgetRequest;
import com.smartfinvo.modules.expense.application.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;

    // Service-to-service: used to calculate currentSpent for each budget
    // Avoids duplicating the expense sum SQL in the budget module
    private final ExpenseService expenseService;

    // ── POST /api/v1/budgets ──────────────────────────────────────────────
    // currentSpent is always 0 on creation — no expenses exist yet for this budget
    @Transactional
    public Mono<BudgetResponse> createBudget(UUID userId, CreateBudgetRequest request) {
        log.debug("Creating budget userId={} name={}", userId, request.getName());

        LocalDate endDate = resolveEndDate(request.getStartDate(), request.getEndDate(), request.getPeriod());

        Budget budget = Budget.builder()
                .userId(userId)
                .name(request.getName())
                .categoryId(request.getCategoryId())
                .limitAmount(request.getLimitAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .period(request.getPeriod())
                .startDate(request.getStartDate())
                .endDate(endDate)
                .alertThreshold(request.getAlertThreshold() != null ? request.getAlertThreshold() : (short) 80)
                .alertEnabled(request.getAlertEnabled() != null ? request.getAlertEnabled() : Boolean.TRUE)
                .alertChannels(channelsToString(request.getAlertChannels()))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        return budgetRepository
                .save(budget)
                // On creation currentSpent = 0, so we skip the expense sum call
                .map(saved -> toResponse(saved, BigDecimal.ZERO))
                .doOnSuccess(resp -> log.info("Budget created budgetId={} userId={}", resp.getId(), userId))
                .doOnError(err -> log.error("Budget creation failed userId={} error={}", userId, err.getMessage()));
    }

    // ── GET /api/v1/budgets/{budgetId} ────────────────────────────────────
    // Fetches the budget then calculates currentSpent via ExpenseService
    public Mono<BudgetResponse> getBudgetById(UUID userId, UUID budgetId) {
        log.debug("Fetching budget budgetId={} userId={}", budgetId, userId);

        return budgetRepository
                .findByIdAndUserIdAndDeletedAtIsNull(budgetId, userId)
                .switchIfEmpty(Mono.error(new RuntimeException("BUDGET_NOT_FOUND")))
                .flatMap(this::withCurrentSpent)
                .doOnError(err -> log.warn("getBudgetById failed budgetId={} reason={}", budgetId, err.getMessage()));
    }

    // ── GET /api/v1/budgets ───────────────────────────────────────────────
    // Lists all active budgets with currentSpent calculated for each.
    // flatMap on a Flux runs the expense sum concurrently per budget.
    public Flux<BudgetResponse> listBudgets(UUID userId) {
        log.debug("Listing budgets userId={}", userId);

        return budgetRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                // flatMap (not map) because withCurrentSpent returns a Mono — runs concurrently
                .flatMap(this::withCurrentSpent)
                .doOnError(err -> log.error("listBudgets failed userId={} error={}", userId, err.getMessage()));
    }

    // ── PATCH /api/v1/budgets/{budgetId} ──────────────────────────────────
    // Partial update — only non-null fields in the request are applied
    @Transactional
    public Mono<BudgetResponse> updateBudget(UUID userId, UUID budgetId, UpdateBudgetRequest request) {
        log.debug("Updating budget budgetId={} userId={}", budgetId, userId);

        return budgetRepository
                .findByIdAndUserIdAndDeletedAtIsNull(budgetId, userId)
                .switchIfEmpty(Mono.error(new RuntimeException("BUDGET_NOT_FOUND")))
                .flatMap(existing -> {
                    if (request.getName() != null) {
                        existing.setName(request.getName());
                    }
                    if (request.getLimitAmount() != null) {
                        existing.setLimitAmount(request.getLimitAmount());
                    }
                    if (request.getAlertThreshold() != null) {
                        existing.setAlertThreshold(request.getAlertThreshold());
                    }
                    if (request.getAlertEnabled() != null) {
                        existing.setAlertEnabled(request.getAlertEnabled());
                    }
                    if (request.getAlertChannels() != null) {
                        existing.setAlertChannels(channelsToString(request.getAlertChannels()));
                    }
                    existing.setUpdatedAt(OffsetDateTime.now());
                    return budgetRepository.save(existing);
                })
                .flatMap(this::withCurrentSpent)
                .doOnSuccess(resp -> log.info("Budget updated budgetId={} userId={}", budgetId, userId))
                .doOnError(err -> log.warn("updateBudget failed budgetId={} reason={}", budgetId, err.getMessage()));
    }

    // ── DELETE /api/v1/budgets/{budgetId} ─────────────────────────────────
    // Soft-delete in one UPDATE statement — no load-then-save needed
    @Transactional
    public Mono<Void> deleteBudget(UUID userId, UUID budgetId) {
        log.debug("Deleting budget budgetId={} userId={}", budgetId, userId);

        return budgetRepository
                .softDeleteByIdAndUserId(budgetId, userId)
                .flatMap(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        // 0 rows means either not found or belongs to another user
                        return Mono.error(new RuntimeException("BUDGET_NOT_FOUND"));
                    }
                    return Mono.empty();
                })
                .then()
                .doOnSuccess(v -> log.info("Budget deleted budgetId={} userId={}", budgetId, userId))
                .doOnError(err -> log.warn("deleteBudget failed budgetId={} reason={}", budgetId, err.getMessage()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    // Calls ExpenseService to sum expenses then builds the full BudgetResponse
    private Mono<BudgetResponse> withCurrentSpent(Budget budget) {
        LocalDate effectiveEnd = budget.effectiveEndDate();

        return expenseService
                .sumByCategoryAndDateRange(
                        budget.getUserId(),
                        budget.getCategoryId(),
                        budget.getStartDate(),
                        effectiveEnd)
                .map(spent -> toResponse(budget, spent));
    }

    // Maps Budget entity + currentSpent → BudgetResponse with all calculated fields
    private BudgetResponse toResponse(Budget budget, BigDecimal currentSpent) {
        BigDecimal limit = budget.getLimitAmount();
        BigDecimal remaining = limit.subtract(currentSpent);

        // percentageUsed = currentSpent / limitAmount * 100, rounded to 1 decimal
        BigDecimal percentageUsed = limit.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : currentSpent.divide(limit, 4, RoundingMode.HALF_UP)
                              .multiply(BigDecimal.valueOf(100))
                              .setScale(1, RoundingMode.HALF_UP);

        String alertStatus = computeAlertStatus(percentageUsed, budget.getAlertThreshold());

        return BudgetResponse.builder()
                .id(budget.getId())
                .userId(budget.getUserId())
                .name(budget.getName())
                .categoryId(budget.getCategoryId())
                .limitAmount(limit)
                .currency(budget.getCurrency())
                .period(budget.getPeriod())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .alertThreshold(budget.getAlertThreshold())
                .alertEnabled(budget.getAlertEnabled())
                .alertChannels(stringToChannels(budget.getAlertChannels()))
                .currentSpent(currentSpent)
                .remainingAmount(remaining)
                .percentageUsed(percentageUsed)
                .alertStatus(alertStatus)
                .version(budget.getVersion())
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build();
    }

    // EXCEEDED if over 100%, WARNING if at or above threshold but under 100%, OK otherwise
    private String computeAlertStatus(BigDecimal percentageUsed, Short alertThreshold) {
        if (percentageUsed.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return "EXCEEDED";
        }
        if (alertThreshold != null
                && percentageUsed.compareTo(BigDecimal.valueOf(alertThreshold)) >= 0) {
            return "WARNING";
        }
        return "OK";
    }

    // Infers endDate from period when the caller didn't provide one
    private LocalDate resolveEndDate(LocalDate startDate, LocalDate endDate, String period) {
        if (endDate != null) return endDate;
        return switch (period) {
            case "MONTHLY"   -> startDate.plusMonths(1).minusDays(1);
            case "QUARTERLY" -> startDate.plusMonths(3).minusDays(1);
            case "YEARLY"    -> startDate.plusYears(1).minusDays(1);
            default          -> null; // CUSTOM with no endDate = open-ended
        };
    }

    // List<String> → comma-separated String for DB storage
    private String channelsToString(List<String> channels) {
        if (channels == null || channels.isEmpty()) return "";
        return String.join(",", channels);
    }

    // Comma-separated String → List<String> for JSON response
    private List<String> stringToChannels(String channels) {
        if (channels == null || channels.isBlank()) return Collections.emptyList();
        return Arrays.asList(channels.split(","));
    }
}

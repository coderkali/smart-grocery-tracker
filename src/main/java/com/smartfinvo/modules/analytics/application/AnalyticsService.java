package com.smartfinvo.modules.analytics.application;

import com.smartfinvo.modules.ai.api.AiModulePort;
import com.smartfinvo.modules.analytics.infrastructure.web.dto.BudgetVsActualResponse;
import com.smartfinvo.modules.analytics.infrastructure.web.dto.ExpenseSummaryResponse;
import com.smartfinvo.modules.analytics.infrastructure.web.dto.InsightResponse;
import com.smartfinvo.modules.analytics.infrastructure.web.dto.SpendingTrendResponse;
import com.smartfinvo.modules.budget.infrastructure.persistence.BudgetRepository;
import com.smartfinvo.modules.expense.infrastructure.persistence.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// Read-only service — no @Transactional needed (all SELECT queries)
// Injects repositories directly rather than going through their services,
// because analytics needs raw Expense/Budget streams for grouping in Java.
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final AiModulePort aiModulePort;

    // ── GET /api/v1/analytics/summary ─────────────────────────────────────
    // Collects all expenses in the range, groups by categoryId, calculates
    // per-category totals and percentages entirely in Java — no complex SQL needed.
    public Mono<ExpenseSummaryResponse> getSummary(UUID userId, LocalDate startDate, LocalDate endDate) {
        log.debug("Analytics summary userId={} {} to {}", userId, startDate, endDate);

        return expenseRepository
                .findByUserIdAndDateRange(userId, startDate, endDate)
                .collectList()
                .map(expenses -> {
                    BigDecimal total = expenses.stream()
                            .map(e -> Objects.requireNonNullElse(e.getAmount(), BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    long count = expenses.size();

                    BigDecimal avg = count > 0
                            ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    // Group by categoryId → sum, count, percentage
                    Map<String, ExpenseSummaryResponse.CategorySummary> byCategory = expenses.stream()
                            .collect(Collectors.groupingBy(
                                    e -> e.getCategoryId().toString(),
                                    Collectors.collectingAndThen(
                                            Collectors.toList(),
                                            list -> {
                                                BigDecimal catTotal = list.stream()
                                                        .map(e -> Objects.requireNonNullElse(e.getAmount(), BigDecimal.ZERO))
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                                BigDecimal pct = total.compareTo(BigDecimal.ZERO) == 0
                                                        ? BigDecimal.ZERO
                                                        : catTotal.divide(total, 4, RoundingMode.HALF_UP)
                                                                  .multiply(BigDecimal.valueOf(100))
                                                                  .setScale(1, RoundingMode.HALF_UP);

                                                return ExpenseSummaryResponse.CategorySummary.builder()
                                                        .total(catTotal)
                                                        .count((long) list.size())
                                                        .percentage(pct)
                                                        .build();
                                            }
                                    )
                            ));

                    return ExpenseSummaryResponse.builder()
                            .totalExpenses(total)
                            .totalCount(count)
                            .averagePerExpense(avg)
                            .byCategory(byCategory)
                            .period(ExpenseSummaryResponse.PeriodInfo.builder()
                                    .startDate(startDate)
                                    .endDate(endDate)
                                    .build())
                            .build();
                })
                .doOnError(err -> log.error("getSummary failed userId={} error={}", userId, err.getMessage()));
    }

    // ── GET /api/v1/analytics/trends ──────────────────────────────────────
    // Groups expenses into day / week / month buckets in Java.
    // week  → Monday of that week
    // month → first day of that month
    // Results sorted oldest-first so the client can render a left-to-right chart.
    public Mono<SpendingTrendResponse> getTrends(
            UUID userId, LocalDate startDate, LocalDate endDate, String groupBy) {

        log.debug("Analytics trends userId={} {} to {} groupBy={}", userId, startDate, endDate, groupBy);

        return expenseRepository
                .findByUserIdAndDateRange(userId, startDate, endDate)
                .collectList()
                .map(expenses -> {
                    // Determine the bucket date for each expense based on groupBy
                    Map<LocalDate, List<com.smartfinvo.modules.expense.domain.Expense>> grouped =
                            expenses.stream().collect(Collectors.groupingBy(e -> {
                                LocalDate d = e.getExpenseDate();
                                return switch (groupBy) {
                                    case "week"  -> d.with(DayOfWeek.MONDAY);
                                    case "month" -> d.withDayOfMonth(1);
                                    default      -> d; // "day"
                                };
                            }));

                    List<SpendingTrendResponse.TrendPoint> trends = grouped.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> {
                                BigDecimal bucketTotal = entry.getValue().stream()
                                        .map(e -> Objects.requireNonNullElse(e.getAmount(), BigDecimal.ZERO))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                return SpendingTrendResponse.TrendPoint.builder()
                                        .date(entry.getKey().toString())
                                        .total(bucketTotal)
                                        .count((long) entry.getValue().size())
                                        .build();
                            })
                            .toList();

                    return SpendingTrendResponse.builder().trends(trends).build();
                })
                .doOnError(err -> log.error("getTrends failed userId={} error={}", userId, err.getMessage()));
    }

    // ── GET /api/v1/analytics/insights ────────────────────────────────────
    // Calculates total spending for the period, maps the date range to a period
    // string the AI understands, then delegates to AiModulePort.analyzeBudget()
    // which runs RAG + GPT-4o + Evaluator. Maps the AI insight records to our DTO.
    public Mono<InsightResponse> getInsights(UUID userId, LocalDate startDate, LocalDate endDate) {
        log.debug("Analytics insights userId={} {} to {}", userId, startDate, endDate);

        return expenseRepository
                .sumTotalByUserIdAndDateRange(userId, startDate, endDate)
                .map(total -> Objects.requireNonNullElse(total, BigDecimal.ZERO))
                .defaultIfEmpty(BigDecimal.ZERO)
                .flatMap(totalSpent -> {
                    // Map the date range to the period string AiModulePort expects
                    long days = ChronoUnit.DAYS.between(startDate, endDate);
                    String period = days <= 35 ? "last_month"
                                  : days <= 100 ? "last_3_months"
                                  : "last_year";

                    // Use total spent as the monthly budget baseline for the AI prompt
                    // The AI compares it against category-level spending it retrieves from the vector store
                    AiModulePort.BudgetAnalysisCommand command =
                            new AiModulePort.BudgetAnalysisCommand(userId, period, totalSpent);

                    return aiModulePort.analyzeBudget(command);
                })
                .map(result -> {
                    List<InsightResponse.InsightItem> items = result.insights().stream()
                            .map(insight -> InsightResponse.InsightItem.builder()
                                    .type(insight.type().name())
                                    .title(insight.finding())
                                    .category(insight.category())
                                    .recommendation(insight.suggestion())
                                    .build())
                            .toList();

                    return InsightResponse.builder().insights(items).build();
                })
                .doOnError(err -> log.error("getInsights failed userId={} error={}", userId, err.getMessage()));
    }

    // ── GET /api/v1/analytics/budget-vs-actual ────────────────────────────
    // For each active budget: sums expenses in its category within the overlap
    // of [budget.startDate, budget.effectiveEndDate] ∩ [startDate, endDate],
    // then calculates variance and status.
    public Mono<BudgetVsActualResponse> getBudgetVsActual(
            UUID userId, LocalDate startDate, LocalDate endDate) {

        log.debug("Analytics budgetVsActual userId={} {} to {}", userId, startDate, endDate);

        return budgetRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .collectList()
                .flatMap(budgets -> {
                    // For each budget, sum expenses in its category within the effective date overlap
                    List<Mono<BudgetVsActualResponse.BudgetVsActualItem>> itemMonos = budgets.stream()
                            .map(budget -> {
                                // Intersect budget dates with query range
                                LocalDate effectiveStart = budget.getStartDate().isAfter(startDate)
                                        ? budget.getStartDate() : startDate;
                                LocalDate effectiveEnd = budget.effectiveEndDate().isBefore(endDate)
                                        ? budget.effectiveEndDate() : endDate;

                                // Skip budgets with no overlap
                                if (effectiveStart.isAfter(effectiveEnd)) {
                                    return Mono.just(buildVsActualItem(budget, BigDecimal.ZERO));
                                }

                                return expenseRepository
                                        .sumAmountByUserIdAndCategoryIdAndDateRange(
                                                userId, budget.getCategoryId(),
                                                effectiveStart, effectiveEnd)
                                        .map(sum -> Objects.requireNonNullElse(sum, BigDecimal.ZERO))
                                        .defaultIfEmpty(BigDecimal.ZERO)
                                        .map(actual -> buildVsActualItem(budget, actual));
                            })
                            .toList();

                    // Run all expense-sum queries concurrently then aggregate
                    return Mono.zip(
                            itemMonos,
                            results -> {
                                List<BudgetVsActualResponse.BudgetVsActualItem> items =
                                        java.util.Arrays.stream(results)
                                                .map(r -> (BudgetVsActualResponse.BudgetVsActualItem) r)
                                                .sorted(Comparator.comparing(
                                                        BudgetVsActualResponse.BudgetVsActualItem::getBudgetName))
                                                .toList();

                                BigDecimal totalBudgeted = items.stream()
                                        .map(BudgetVsActualResponse.BudgetVsActualItem::getBudgeted)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                BigDecimal totalActual = items.stream()
                                        .map(BudgetVsActualResponse.BudgetVsActualItem::getActual)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                return BudgetVsActualResponse.builder()
                                        .report(items)
                                        .totalBudgeted(totalBudgeted)
                                        .totalActual(totalActual)
                                        .totalVariance(totalBudgeted.subtract(totalActual))
                                        .build();
                            }
                    // Mono.zip requires at least one source — return empty report if no budgets
                    ).switchIfEmpty(Mono.just(BudgetVsActualResponse.builder()
                            .report(List.of())
                            .totalBudgeted(BigDecimal.ZERO)
                            .totalActual(BigDecimal.ZERO)
                            .totalVariance(BigDecimal.ZERO)
                            .build()));
                })
                .doOnError(err -> log.error("getBudgetVsActual failed userId={} error={}", userId, err.getMessage()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private BudgetVsActualResponse.BudgetVsActualItem buildVsActualItem(
            com.smartfinvo.modules.budget.domain.Budget budget, BigDecimal actual) {

        BigDecimal budgeted = budget.getLimitAmount();
        BigDecimal variance = budgeted.subtract(actual);

        BigDecimal variancePct = budgeted.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : variance.divide(budgeted, 4, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100))
                          .setScale(1, RoundingMode.HALF_UP);

        return BudgetVsActualResponse.BudgetVsActualItem.builder()
                .budgetId(budget.getId())
                .budgetName(budget.getName())
                .categoryId(budget.getCategoryId())
                .budgeted(budgeted)
                .actual(actual)
                .variance(variance)
                .variancePercent(variancePct)
                .status(actual.compareTo(budgeted) >= 0 ? "OVER" : "UNDER")
                .build();
    }
}

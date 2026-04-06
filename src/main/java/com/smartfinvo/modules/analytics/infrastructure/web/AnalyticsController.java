package com.smartfinvo.modules.analytics.infrastructure.web;

import com.smartfinvo.modules.analytics.application.AnalyticsService;
import com.smartfinvo.modules.analytics.infrastructure.web.dto.BudgetVsActualResponse;
import com.smartfinvo.modules.analytics.infrastructure.web.dto.ExpenseSummaryResponse;
import com.smartfinvo.modules.analytics.infrastructure.web.dto.InsightResponse;
import com.smartfinvo.modules.analytics.infrastructure.web.dto.SpendingTrendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Spending analytics — summary, trends, AI insights, and budget vs actual comparisons.")
@SecurityRequirement(name = "BearerAuth")
public class AnalyticsController {

    private static final Set<String> VALID_GROUP_BY = Set.of("day", "week", "month");

    private final AnalyticsService analyticsService;

    // ── GET /api/v1/analytics/summary ─────────────────────────────────────
    @Operation(
            summary = "Expense summary",
            description = """
                    Returns total spending, count, average, and a breakdown by category
                    for the given date range.
                    Both `start_date` and `end_date` are required (format: YYYY-MM-DD).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary calculated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExpenseSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid date parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/summary")
    public Mono<ResponseEntity<ExpenseSummaryResponse>> getSummary(
            @Parameter(description = "Start date (inclusive, YYYY-MM-DD)", required = true, example = "2026-04-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start_date,

            @Parameter(description = "End date (inclusive, YYYY-MM-DD)", required = true, example = "2026-04-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end_date,

            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        if (end_date.isBefore(start_date)) {
            log.warn("GET /analytics/summary — end_date before start_date userId={}", userId);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return analyticsService
                .getSummary(userId, start_date, end_date)
                .map(ResponseEntity::ok)
                .onErrorResume(err -> {
                    log.error("GET /analytics/summary — error userId={} error={}", userId, err.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ── GET /api/v1/analytics/trends ──────────────────────────────────────
    @Operation(
            summary = "Spending trends (time series)",
            description = """
                    Returns a time series of spending data grouped by `day`, `week`, or `month`.
                    Each data point shows total amount and count for that time bucket.
                    Buckets with no spending are omitted (sparse series).
                    Default `group_by` is `day`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trend data calculated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpendingTrendResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range or group_by value"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/trends")
    public Mono<ResponseEntity<SpendingTrendResponse>> getTrends(
            @Parameter(description = "Start date (inclusive, YYYY-MM-DD)", required = true, example = "2026-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start_date,

            @Parameter(description = "End date (inclusive, YYYY-MM-DD)", required = true, example = "2026-04-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end_date,

            @Parameter(description = "Grouping granularity: day, week, or month. Default: day.",
                    schema = @Schema(allowableValues = {"day", "week", "month"}))
            @RequestParam(defaultValue = "day") String group_by,

            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        if (end_date.isBefore(start_date)) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        if (!VALID_GROUP_BY.contains(group_by)) {
            log.warn("GET /analytics/trends — invalid group_by={} userId={}", group_by, userId);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return analyticsService
                .getTrends(userId, start_date, end_date, group_by)
                .map(ResponseEntity::ok)
                .onErrorResume(err -> {
                    log.error("GET /analytics/trends — error userId={} error={}", userId, err.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ── GET /api/v1/analytics/insights ────────────────────────────────────
    @Operation(
            summary = "AI-powered spending insights",
            description = """
                    Analyses spending for the given date range using GPT-4o with RAG on purchase history.
                    Returns 2–4 actionable insights about patterns, overspending, and savings opportunities.
                    Results may take 3–5 seconds due to the LLM call.
                    Returns 503 if the AI service is unavailable.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Insights generated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = InsightResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "503", description = "AI service unavailable")
    })
    @GetMapping("/insights")
    public Mono<ResponseEntity<InsightResponse>> getInsights(
            @Parameter(description = "Start date (inclusive, YYYY-MM-DD)", required = true, example = "2026-04-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start_date,

            @Parameter(description = "End date (inclusive, YYYY-MM-DD)", required = true, example = "2026-04-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end_date,

            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        if (end_date.isBefore(start_date)) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return analyticsService
                .getInsights(userId, start_date, end_date)
                .map(ResponseEntity::ok)
                .onErrorResume(err -> {
                    log.error("GET /analytics/insights — error userId={} error={}", userId, err.getMessage());
                    // AI failures return 503 — distinct from a 500 server error
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
                });
    }

    // ── GET /api/v1/analytics/budget-vs-actual ────────────────────────────
    @Operation(
            summary = "Budget vs actual spending",
            description = """
                    Compares each active budget against actual spending within the date range.
                    The date range is intersected with each budget's own start/end dates,
                    so partial-period budgets are handled correctly.
                    Returns an empty report if the user has no budgets.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comparison report generated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BudgetVsActualResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/budget-vs-actual")
    public Mono<ResponseEntity<BudgetVsActualResponse>> getBudgetVsActual(
            @Parameter(description = "Start date (inclusive, YYYY-MM-DD)", required = true, example = "2026-04-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start_date,

            @Parameter(description = "End date (inclusive, YYYY-MM-DD)", required = true, example = "2026-04-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end_date,

            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        if (end_date.isBefore(start_date)) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return analyticsService
                .getBudgetVsActual(userId, start_date, end_date)
                .map(ResponseEntity::ok)
                .onErrorResume(err -> {
                    log.error("GET /analytics/budget-vs-actual — error userId={} error={}", userId, err.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}

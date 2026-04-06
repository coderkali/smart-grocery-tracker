package com.smartfinvo.modules.analytics.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Aggregated expense summary for a given date range.")
public class ExpenseSummaryResponse {

    @JsonProperty("total_expenses")
    @Schema(description = "Total amount spent in the period", example = "342.50")
    private BigDecimal totalExpenses;

    @JsonProperty("total_count")
    @Schema(description = "Number of expense records in the period", example = "28")
    private Long totalCount;

    @JsonProperty("average_per_expense")
    @Schema(description = "Average amount per expense record", example = "12.23")
    private BigDecimal averagePerExpense;

    // Key = categoryId (UUID as String), value = per-category breakdown
    // Using String key so Jackson serialises the map as a JSON object without issues
    @JsonProperty("by_category")
    @Schema(description = "Spending breakdown keyed by category ID")
    private Map<String, CategorySummary> byCategory;

    @Schema(description = "The date range this summary covers")
    private PeriodInfo period;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Spending figures for a single category.")
    public static class CategorySummary {

        @Schema(description = "Total spent in this category", example = "89.90")
        private BigDecimal total;

        @Schema(description = "Number of expenses in this category", example = "7")
        private Long count;

        @Schema(description = "Percentage of total spending (0–100)", example = "26.2")
        private BigDecimal percentage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Date range covered by this report.")
    public static class PeriodInfo {

        @JsonProperty("start_date")
        @Schema(description = "Inclusive start date", example = "2026-04-01")
        private LocalDate startDate;

        @JsonProperty("end_date")
        @Schema(description = "Inclusive end date", example = "2026-04-30")
        private LocalDate endDate;
    }
}

package com.smartfinvo.modules.analytics.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Budget vs actual spending comparison for a given date range.")
public class BudgetVsActualResponse {

    // One row per active budget the user has
    @Schema(description = "Per-budget comparison rows")
    private List<BudgetVsActualItem> report;

    @JsonProperty("total_budgeted")
    @Schema(description = "Sum of all budget limit amounts", example = "500.00")
    private BigDecimal totalBudgeted;

    @JsonProperty("total_actual")
    @Schema(description = "Sum of all actual spending across budgets", example = "342.50")
    private BigDecimal totalActual;

    // Positive = under budget (good), negative = over budget (bad)
    @JsonProperty("total_variance")
    @Schema(description = "totalBudgeted − totalActual. Positive = under budget.", example = "157.50")
    private BigDecimal totalVariance;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Budget vs actual figures for a single budget.")
    public static class BudgetVsActualItem {

        @JsonProperty("budget_id")
        @Schema(description = "Budget UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID budgetId;

        @JsonProperty("budget_name")
        @Schema(description = "Budget name", example = "Monthly Groceries")
        private String budgetName;

        @JsonProperty("category_id")
        @Schema(description = "Category UUID this budget tracks", example = "22222222-2222-2222-2222-222222222222")
        private UUID categoryId;

        @Schema(description = "Budget limit amount", example = "300.00")
        private BigDecimal budgeted;

        @Schema(description = "Actual amount spent in this category within the period", example = "142.50")
        private BigDecimal actual;

        // budgeted − actual: positive = under budget, negative = over budget
        @Schema(description = "budgeted − actual (positive = under, negative = over)", example = "157.50")
        private BigDecimal variance;

        @JsonProperty("variance_percent")
        @Schema(description = "variance / budgeted × 100 (positive = under %)", example = "52.5")
        private BigDecimal variancePercent;

        // UNDER | OVER
        @Schema(description = "UNDER if actual < budgeted, OVER if actual >= budgeted",
                example = "UNDER", allowableValues = {"UNDER", "OVER"})
        private String status;
    }
}

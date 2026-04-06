package com.smartfinvo.modules.budget.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Budget record returned by the API, including calculated spending figures.")
public class BudgetResponse {

    @Schema(description = "Unique budget ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @JsonProperty("user_id")
    @Schema(description = "Owner user ID", example = "11111111-1111-1111-1111-111111111111")
    private UUID userId;

    @Schema(description = "Budget name", example = "Monthly Groceries")
    private String name;

    @JsonProperty("category_id")
    @Schema(description = "Expense category this budget tracks", example = "22222222-2222-2222-2222-222222222222")
    private UUID categoryId;

    @JsonProperty("limit_amount")
    @Schema(description = "Spending cap for the period", example = "300.00")
    private BigDecimal limitAmount;

    @Schema(description = "Currency code (ISO 4217)", example = "USD")
    private String currency;

    @Schema(description = "Budget period type", example = "MONTHLY",
            allowableValues = {"MONTHLY", "QUARTERLY", "YEARLY", "CUSTOM"})
    private String period;

    @JsonProperty("start_date")
    @Schema(description = "Period start date (inclusive)", example = "2026-04-01")
    private LocalDate startDate;

    @JsonProperty("end_date")
    @Schema(description = "Period end date (inclusive). Null = open-ended.", example = "2026-04-30")
    private LocalDate endDate;

    @JsonProperty("alert_threshold")
    @Schema(description = "Alert fires when spending reaches this % of limit", example = "80")
    private Short alertThreshold;

    @JsonProperty("alert_enabled")
    @Schema(description = "Whether alerts are active", example = "true")
    private Boolean alertEnabled;

    @JsonProperty("alert_channels")
    @Schema(description = "Channels to notify on alert", example = "[\"email\",\"push\"]")
    private List<String> alertChannels;

    // ── Calculated fields — computed at read time, never stored ──────────

    @JsonProperty("current_spent")
    @Schema(description = "Total expenses in this budget's category within the period", example = "142.50")
    private BigDecimal currentSpent;

    @JsonProperty("remaining_amount")
    @Schema(description = "limitAmount − currentSpent", example = "157.50")
    private BigDecimal remainingAmount;

    @JsonProperty("percentage_used")
    @Schema(description = "currentSpent / limitAmount × 100, rounded to 1 decimal", example = "47.5")
    private BigDecimal percentageUsed;

    // OK | WARNING | EXCEEDED
    // WARNING: percentageUsed >= alertThreshold AND < 100
    // EXCEEDED: percentageUsed >= 100
    @JsonProperty("alert_status")
    @Schema(description = "Current alert status", example = "OK",
            allowableValues = {"OK", "WARNING", "EXCEEDED"})
    private String alertStatus;

    @Schema(description = "Optimistic locking version", example = "0")
    private Integer version;

    @JsonProperty("created_at")
    @Schema(description = "Creation timestamp (ISO-8601 UTC)", example = "2026-04-01T00:00:00Z")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    @Schema(description = "Last update timestamp (ISO-8601 UTC)", example = "2026-04-01T00:00:00Z")
    private OffsetDateTime updatedAt;
}

package com.smartfinvo.modules.budget.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for creating a new budget.")
public class CreateBudgetRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Schema(description = "Budget name (2–100 chars)", example = "Monthly Groceries", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "category_id is required")
    @JsonProperty("category_id")
    @Schema(description = "Expense category this budget tracks", example = "22222222-2222-2222-2222-222222222222", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID categoryId;

    @NotNull(message = "limit_amount is required")
    @DecimalMin(value = "0.01", message = "Limit amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Limit amount is too large")
    @JsonProperty("limit_amount")
    @Schema(description = "Spending cap for the period (> 0)", example = "300.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal limitAmount;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
    @Schema(description = "Currency code (ISO 4217). Defaults to USD.", example = "USD")
    private String currency;

    @NotBlank(message = "period is required")
    @Pattern(regexp = "MONTHLY|QUARTERLY|YEARLY|CUSTOM",
             message = "period must be MONTHLY, QUARTERLY, YEARLY, or CUSTOM")
    @Schema(description = "Budget period type", example = "MONTHLY",
            allowableValues = {"MONTHLY", "QUARTERLY", "YEARLY", "CUSTOM"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String period;

    @NotNull(message = "start_date is required")
    @JsonProperty("start_date")
    @Schema(description = "Period start date (inclusive, YYYY-MM-DD)", example = "2026-04-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate startDate;

    // Optional — if omitted the service infers it from period + startDate
    @JsonProperty("end_date")
    @Schema(description = "Period end date (inclusive, YYYY-MM-DD). If omitted, inferred from period.", example = "2026-04-30")
    private LocalDate endDate;

    @Min(value = 0, message = "Alert threshold must be between 0 and 100")
    @Max(value = 100, message = "Alert threshold must be between 0 and 100")
    @JsonProperty("alert_threshold")
    @Schema(description = "Alert fires when spending reaches this % of limit (0–100). Defaults to 80.", example = "80")
    private Short alertThreshold;

    @JsonProperty("alert_enabled")
    @Schema(description = "Whether alerts are enabled. Defaults to true.", example = "true")
    private Boolean alertEnabled;

    @JsonProperty("alert_channels")
    @Schema(description = "Notification channels. Defaults to empty list.", example = "[\"email\", \"push\"]")
    private List<String> alertChannels;
}

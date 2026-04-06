package com.smartfinvo.modules.budget.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// All fields optional — only non-null fields are applied (same pattern as UpdateExpenseRequest)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for updating a budget. All fields are optional — only provided fields are changed.")
public class UpdateBudgetRequest {

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Schema(description = "Updated budget name (2–100 chars)", example = "Weekly Groceries")
    private String name;

    @DecimalMin(value = "0.01", message = "Limit amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Limit amount is too large")
    @JsonProperty("limit_amount")
    @Schema(description = "Updated spending cap", example = "350.00")
    private BigDecimal limitAmount;

    @Min(value = 0, message = "Alert threshold must be between 0 and 100")
    @Max(value = 100, message = "Alert threshold must be between 0 and 100")
    @JsonProperty("alert_threshold")
    @Schema(description = "Updated alert threshold percentage (0–100)", example = "75")
    private Short alertThreshold;

    @JsonProperty("alert_enabled")
    @Schema(description = "Enable or disable alerts", example = "false")
    private Boolean alertEnabled;

    @JsonProperty("alert_channels")
    @Schema(description = "Updated notification channels", example = "[\"email\"]")
    private List<String> alertChannels;
}

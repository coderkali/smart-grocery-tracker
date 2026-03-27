package com.smartfinvo.modules.expense.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Expense record returned by the API")
public class ExpenseResponse {

    private UUID id;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("category_id")
    private UUID categoryId;

    private BigDecimal amount;

    private String currency;

    private String description;

    @JsonProperty("expense_date")
    private LocalDate expenseDate;

    @JsonProperty("payment_method")
    private String paymentMethod;

    private String tags;

    @JsonProperty("receipt_url")
    private String receiptUrl;

    private String notes;

    private Integer version;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
}
